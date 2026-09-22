package com.johnny.hotel;

import com.johnny.hotel.businessdate.*;
import com.johnny.hotel.payment.*;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=HotelBackendApplication.class,properties={"hotel.wallet.dev.fixture=true","spring.cache.type=none","logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class BusinessDateDevelopmentTest {
    @DynamicPropertySource static void development(DynamicPropertyRegistry r){var e=WalletDevelopmentGuard.settings();
        r.add("spring.datasource.url",WalletDevelopmentGuard::url);r.add("spring.datasource.username",()->e.get("DB_USERNAME"));
        r.add("spring.datasource.password",()->e.get("DB_PASSWORD"));r.add("spring.data.redis.password",()->e.get("REDIS_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;@Autowired BusinessDateService dates;@Autowired PlatformTransactionManager transactions;@Autowired MockMvc mvc;@Autowired PaymentRecognitionService recognition;

    @Test void migrationBootstrapAndRestartAreStable() {
        var before=dates.current();long version=before.getVersion();int history=jdbc.queryForObject("SELECT COUNT(*) FROM hotel_business_date_history WHERE event_type='INITIALIZED'",Integer.class);
        dates.initializeOnce();
        var after=dates.current();assertEquals(before.getBusinessDate(),after.getBusinessDate());assertEquals(version,after.getVersion());assertEquals(1,history);
        assertEquals(history,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_business_date_history WHERE event_type='INITIALIZED'",Integer.class));
        assertEquals("45",jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",String.class));
    }

    @Test void readApiEnforcesOperationalRolesAndSeparatesWallClock() throws Exception {
        for(String role:List.of("STAFF","FINANCE","MANAGER","OWNER","SUPER_ADMIN"))
            mvc.perform(get("/api/admin/business-date").with(authentication(auth(role)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessDate").value(dates.current().getBusinessDate().toString()))
                .andExpect(jsonPath("$.data.hotelZone").value(ZoneId.systemDefault().getId()))
                .andExpect(jsonPath("$.data.currentWallClockTime").exists());
        for(String role:List.of("CUSTOMER","HR_ADMIN"))mvc.perform(get("/api/admin/business-date").with(authentication(auth(role)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/business-date")).andExpect(status().isUnauthorized());
    }

    @Test void sharedPostingLocksCoexistAndExclusiveAdvanceWaits() throws Exception {
        var firstHasShared=new CountDownLatch(1);var releaseFirst=new CountDownLatch(1);var exclusiveStarted=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(3);
        try {
            Future<LocalDate> first=pool.submit(()->tx(()->{var d=dates.postingDate();firstHasShared.countDown();await(releaseFirst);return d;}));
            assertTrue(firstHasShared.await(5,TimeUnit.SECONDS));
            Future<LocalDate> second=pool.submit(()->tx(dates::postingDate));
            assertEquals(dates.current().getBusinessDate(),second.get(5,TimeUnit.SECONDS),"normal postings must not serialize each other");
            Future<LocalDate> exclusive=pool.submit(()->{exclusiveStarted.countDown();return tx(()->dates.lockForFutureAdvance().getBusinessDate());});
            assertTrue(exclusiveStarted.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->exclusive.get(300,TimeUnit.MILLISECONDS));
            releaseFirst.countDown();assertEquals(dates.current().getBusinessDate(),first.get(5,TimeUnit.SECONDS));assertEquals(dates.current().getBusinessDate(),exclusive.get(5,TimeUnit.SECONDS));
        } finally {releaseFirst.countDown();pool.shutdownNow();}
    }

    @Test void realMysqlFirstSuccessFreezesDateAndDuplicateCannotOverwriteIt() {
        long user=jdbc.queryForObject("SELECT id FROM sys_user ORDER BY id LIMIT 1",Long.class),roomType=jdbc.queryForObject("SELECT id FROM room_type ORDER BY id LIMIT 1",Long.class);
        String suffix=java.util.UUID.randomUUID().toString().replace("-","").substring(0,16),merchant="bd_merchant_"+suffix,request="bd_request_"+suffix,claim="bd_claim_"+suffix;
        jdbc.update("INSERT INTO reservation_checkout_session(customer_user_id,room_type_id,check_in_date,check_out_date,guest_count,currency,quoted_total,request_key,status,expires_at) VALUES(?,?,CURRENT_DATE,DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY),1,'CNY',100.00,?,'PAYMENT_PENDING',DATE_ADD(NOW(6),INTERVAL 1 HOUR))",user,roomType,request);
        long checkout=jdbc.queryForObject("SELECT id FROM reservation_checkout_session WHERE customer_user_id=? AND request_key=?",Long.class,user,request);
        jdbc.update("INSERT INTO payment_attempt(checkout_session_id,purpose,provider,request_key,merchant_payment_no,provider_payment_id,amount,currency,status,fulfillment_status,initiator_type,initiated_by_user_id,recovery_claim_token,recovery_claim_until) VALUES(?,'RESERVATION_DEPOSIT','MOCK',?,?,?,100.00,'CNY','PENDING','PENDING','CUSTOMER',?,?,DATE_ADD(NOW(6),INTERVAL 1 MINUTE))",checkout,request,merchant,"provider_"+suffix,user,claim);
        long attempt=jdbc.queryForObject("SELECT id FROM payment_attempt WHERE merchant_payment_no=?",Long.class,merchant);
        var result=new ProviderPaymentResult("MOCK",merchant,"provider_"+suffix,"SUCCEEDED",new java.math.BigDecimal("100.00"),"CNY",LocalDateTime.now());
        try {
            recognition.recognizeQuery(attempt,claim,result);
            assertEquals(dates.current().getBusinessDate(),jdbc.queryForObject("SELECT recognized_business_date FROM payment_attempt WHERE id=?",LocalDate.class,attempt));
            jdbc.update("UPDATE payment_attempt SET recovery_claim_token=?,recovery_claim_until=DATE_ADD(NOW(6),INTERVAL 1 MINUTE) WHERE id=?",claim,attempt);
            recognition.recognizeQuery(attempt,claim,result);
            assertEquals(dates.current().getBusinessDate(),jdbc.queryForObject("SELECT recognized_business_date FROM payment_attempt WHERE id=?",LocalDate.class,attempt));
        } finally {jdbc.update("DELETE FROM payment_attempt WHERE id=?",attempt);jdbc.update("DELETE FROM reservation_checkout_session WHERE id=?",checkout);}
    }

    @Test void legacyFactsRemainNullAndAnyDatedTransferPairMatches() {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM deposit_payment WHERE business_date IS NULL",Integer.class)>0,
                "V45 must not invent dates for legacy receipts");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_transfer t JOIN payment p ON p.id=t.payment_id WHERE t.business_date IS NOT NULL AND NOT(t.business_date<=>p.business_date)",Integer.class));
    }

    private <T>T tx(java.util.concurrent.Callable<T> body){return new TransactionTemplate(transactions).execute(status->{try{return body.call();}catch(Exception e){throw new RuntimeException(e);}});}
    private void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new IllegalStateException("latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
    private UsernamePasswordAuthenticationToken auth(String role){var a=new UsernamePasswordAuthenticationToken("test","n/a",List.of(new SimpleGrantedAuthority("ROLE_"+role)));a.setDetails(1L);return a;}
}
