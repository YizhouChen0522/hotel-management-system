package com.johnny.hotel;

import com.johnny.hotel.security.AuthRateLimiter;
import com.johnny.hotel.support.RedisMysqlTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests",matches="true")
class AuthRateLimitIntegrationTest extends RedisMysqlTest {
    @Autowired AuthRateLimiter limiter;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;

    @Test void thresholdExpiryAndIndependentClients(){
        settings.setAuthWindow(Duration.ofMillis(250));
        for(int i=0;i<3;i++)assertTrue(limiter.acquire("login","192.0.2.1").allowed());
        assertFalse(limiter.acquire("login","192.0.2.1").allowed());
        assertTrue(limiter.acquire("login","192.0.2.2").allowed());
        assertTrue(limiter.acquire("register","192.0.2.1").allowed());
        String key=limiter.key("login","192.0.2.1");
        assertFalse(key.contains("192.0.2.1"));
        assertTrue(redis.getExpire(key,TimeUnit.MILLISECONDS)>0);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->!redis.hasKey(key));
        assertTrue(limiter.acquire("login","192.0.2.1").allowed());
    }

    @Test void parallelRequestsAreAtomicAndDoNotExtendWindow()throws Exception{
        var pool=Executors.newFixedThreadPool(12);var start=new CountDownLatch(1);
        var results=new ArrayList<Future<Boolean>>();
        try{
            for(int i=0;i<24;i++)results.add(pool.submit(()->{start.await();return limiter.acquire("login","192.0.2.9").allowed();}));
            start.countDown();int allowed=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))allowed++;
            assertEquals(3,allowed);String key=limiter.key("login","192.0.2.9");assertEquals("3",redis.opsForValue().get(key));
            redis.expire(key,Duration.ofSeconds(5));assertFalse(limiter.acquire("login","192.0.2.9").allowed());assertTrue(redis.getExpire(key)<=5);
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void forwardedHeadersCannotEvadeLimitAndErrorsUseResult()throws Exception{
        for(int i=0;i<3;i++)mvc.perform(post("/api/auth/login").header("X-Forwarded-For","192.0.2."+i).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For","198.51.100.1").contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429)).andExpect(header().exists("Retry-After"));
        mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr("192.0.2.99");return r;}).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void customerAndEmployeeRegistrationsShareBudget()throws Exception{
        for(String kind:new String[]{"customer","employee"})mvc.perform(post("/api/auth/register/"+kind).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register/customer").contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void encodedLoginPathUsesSameLimit()throws Exception{
        for(int i=0;i<3;i++)mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(java.net.URI.create("/api/auth/%6cogin")).contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test void successfulLoginStillChecksPasswordAndIssuesWorkingJwt()throws Exception{
        jdbc.update("UPDATE sys_user SET email=?,password=? WHERE id=2","redis-test@example.test",passwords.encode("test-only-password"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"redis-test@example.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"redis-test@example.test\",\"password\":\"test-only-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString()).andReturn();
        String token=mapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asString();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(2));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void redisFailureClosesOnlyAuthEndpointsAndNeverBypassesSecurity()throws Exception{
        doThrow(new RedisConnectionFailureException("test unavailable")).when(connectionFactory).getConnection();
        try{
            mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503)).andExpect(header().string("Retry-After","1"));
            mvc.perform(post("/api/auth/register/customer").contentType("application/json").content("{}"))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/admin/room-types/1")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/room-types/1").with(user("staff").roles("STAFF")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
            mvc.perform(get("/api/admin/room-types/1").with(user("hr").roles("HR_ADMIN"))).andExpect(status().isForbidden());
        }finally{reset(connectionFactory);}
    }
}
