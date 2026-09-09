package com.johnny.hotel.support;
import com.johnny.hotel.config.HotelRedisProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Import(RedisMysqlTest.RedisTestConfig.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
public abstract class RedisMysqlTest extends IsolatedMysqlTest {
    static final String PREFIX="hotel:redis-test:"+UUID.randomUUID()+":";
    @DynamicPropertySource static void isolatedRedis(DynamicPropertyRegistry r){
        r.add("spring.data.redis.host",()->"127.0.0.1");r.add("spring.data.redis.port",()->16379);r.add("spring.data.redis.database",()->14);
        r.add("spring.data.redis.password",()->"");r.add("spring.data.redis.timeout",()->"300ms");r.add("spring.data.redis.connect-timeout",()->"300ms");
        r.add("hotel.redis.prefix",()->PREFIX);r.add("hotel.redis.login-limit",()->3);r.add("hotel.redis.registration-limit",()->2);
    }
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected HotelRedisProperties settings;
    @Autowired protected SqlCounts counts;
    @MockitoSpyBean protected RedisConnectionFactory connectionFactory;
    @TestConfiguration static class RedisTestConfig {@Bean SqlCounts sqlCounts(){return new SqlCounts();}}
    @BeforeEach void resetRedis(){
        try(var connection=connectionFactory.getConnection()){
            var info=connection.serverCommands().info("server");assertNotNull(info);assertEquals("16379",info.getProperty("tcp_port"));
            assertTrue(info.getProperty("config_file","").contains(".redis-test"),"Unexpected Redis instance");
        }
        clearOwnKeys();counts.clear();settings.setAuthWindow(java.time.Duration.ofMinutes(1));
    }
    @AfterEach void cleanupRedis(){org.mockito.Mockito.reset(connectionFactory);clearOwnKeys();}
    protected void clearOwnKeys(){try(var keys=redis.scan(ScanOptions.scanOptions().match(PREFIX+"*").count(128).build())){keys.forEachRemaining(redis::delete);}}
    @Intercepts(@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    public static class SqlCounts implements Interceptor {
        private final ConcurrentHashMap<String,AtomicInteger> counts=new ConcurrentHashMap<>();
        public int get(String name){return counts.getOrDefault("com.johnny.hotel.mapper."+name,new AtomicInteger()).get();}
        public void clear(){counts.clear();}
        public Object intercept(Invocation i)throws Throwable{counts.computeIfAbsent(((MappedStatement)i.getArgs()[0]).getId(),k->new AtomicInteger()).incrementAndGet();return i.proceed();}
    }
}
