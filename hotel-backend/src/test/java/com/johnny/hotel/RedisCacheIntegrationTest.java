package com.johnny.hotel;
import com.johnny.hotel.support.*;
import com.johnny.hotel.config.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests",matches="true")
class RedisCacheIntegrationTest extends RedisMysqlTest {
    @Autowired RoomTypeService types;@Autowired RoomRateService rates;@Autowired PricingService pricing;@Autowired CacheManager cache;@Autowired MockMvc mvc;
    RoomTypeRequest type(String name){var r=new RoomTypeRequest();r.setTypeName(name);r.setBasePrice(new BigDecimal("125"));r.setCapacity(4);r.setDescription("display");return r;}
    RoomRateRequest rate(String price){return RoomRateRequest.builder().price(new BigDecimal(price)).rateSource("MANUAL").description("override").build();}
    String detail(long id){return settings.getPrefix()+"roomTypeDetail:"+id;}
    String list(){return settings.getPrefix()+"roomTypeList:all";}
    String range(long id,java.time.LocalDate start,java.time.LocalDate end){return settings.getPrefix()+"roomRateRange:"+id+":"+start+":"+end;}
    void ttl(String key,long expectedSeconds){long ttl=redis.getExpire(key,TimeUnit.SECONDS);assertTrue(ttl>expectedSeconds-5&&ttl<=expectedSeconds,"TTL "+ttl);}
    @Test void detailAndListMissHitTypedJsonAndConfiguredTtls(){
        var first=types.getRoomTypeById(1L);assertEquals(first,types.getRoomTypeById(1L));assertEquals(1,counts.get("RoomTypeMapper.selectById"));
        var all=types.getRoomTypes();var cached=types.getRoomTypes();assertEquals(all,cached);assertInstanceOf(RoomTypeVO.class,cached.get(0));assertNotNull(cached.get(0).getCreateTime());assertEquals(1,counts.get("RoomTypeMapper.selectAll"));
        assertTrue(redis.opsForValue().get(detail(1)).startsWith("{"));assertTrue(redis.opsForValue().get(list()).startsWith("["));
        assertFalse(redis.opsForValue().get(list()).contains("@class"));ttl(detail(1),1800);ttl(list(),600);
    }
    @Test void createClearsOnlyList(){types.getRoomTypes();types.getRoomTypeById(1L);types.createRoomType(type("new"));assertFalse(redis.hasKey(list()));assertTrue(redis.hasKey(detail(1)));assertEquals(3,types.getRoomTypes().size());}
    @ParameterizedTest @ValueSource(strings={"update","enable","disable"}) void mutationsEvictDetailAndListButKeepOtherDetail(String action){
        types.getRoomTypeById(1L);types.getRoomTypeById(2L);types.getRoomTypes();
        switch(action){case "update"->types.updateRoomType(1L,type("changed"));case "enable"->types.enableRoomType(1L);default->types.disableRoomType(1L);}
        assertFalse(redis.hasKey(detail(1)));assertFalse(redis.hasKey(list()));assertTrue(redis.hasKey(detail(2)));assertEquals(2,types.getRoomTypes().size());
    }
    @Test void invalidationReallyOccursOnlyAfterOuterTransactionCommit(){types.getRoomTypeById(1L);types.getRoomTypes();
        new TransactionTemplate(txManager).executeWithoutResult(s->{types.updateRoomType(1L,type("committed"));assertTrue(redis.hasKey(detail(1)));assertTrue(redis.hasKey(list()));});
        assertFalse(redis.hasKey(detail(1)));assertFalse(redis.hasKey(list()));assertEquals("committed",types.getRoomTypeById(1L).getTypeName());}
    @Test void rollbackDoesNotEvictOrCacheUncommittedState(){types.getRoomTypeById(1L);types.getRoomTypes();
        new TransactionTemplate(txManager).executeWithoutResult(s->{types.updateRoomType(1L,type("rolled back"));assertEquals("rolled back",types.getRoomTypeById(1L).getTypeName());s.setRollbackOnly();});
        assertTrue(redis.hasKey(detail(1)));assertEquals("test_standard",types.getRoomTypeById(1L).getTypeName());assertEquals("test_standard",jdbc.queryForObject("SELECT type_name FROM room_type WHERE id=1",String.class));}
    @Test void databaseFailurePreservesCachedValues(){types.getRoomTypeById(1L);types.getRoomTypes();gate.arm(Thread.currentThread().getName(),"RoomTypeMapper.update",true);
        assertThrows(Exception.class,()->types.updateRoomType(1L,type("fail")));assertTrue(redis.hasKey(detail(1)));assertTrue(redis.hasKey(list()));assertEquals("test_standard",types.getRoomTypeById(1L).getTypeName());}
    @Test void managerWriteStaffReadSharePublicCache()throws Exception{
        var manager=new UsernamePasswordAuthenticationToken("manager",null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));manager.setDetails(2L);
        var staff=new UsernamePasswordAuthenticationToken("staff",null,List.of(new SimpleGrantedAuthority("ROLE_STAFF")));staff.setDetails(2L);
        types.getRoomTypeById(1L);types.getRoomTypes();mvc.perform(put("/api/admin/room-types/1").with(authentication(manager)).contentType("application/json").content("{\"typeName\":\"public\",\"basePrice\":125,\"capacity\":4}" )).andExpect(status().isOk());counts.clear();
        for(int i=0;i<2;i++)mvc.perform(get("/api/admin/room-types/1").with(authentication(staff))).andExpect(status().isOk()).andExpect(jsonPath("$.data.typeName").value("public"));assertEquals(1,counts.get("RoomTypeMapper.selectById"));
    }
    @Test void rateRangeKeysHaveAllDimensionsAndTypedRoundTrip(){rates.save(1L,arrival,rate("140"));counts.clear();var result=rates.range(1L,arrival,arrival.plusDays(2));assertEquals(result,rates.range(1L,arrival,arrival.plusDays(2)));assertInstanceOf(RoomRateVO.class,result.get(0));
        rates.range(1L,arrival.plusDays(1),arrival.plusDays(2));rates.range(2L,arrival,arrival.plusDays(2));assertEquals(3,counts.get("RoomRateMapper.selectByRoomTypeIdAndDateRange"));ttl(range(1,arrival,arrival.plusDays(2)),60);
    }
    @Test void insertUpdateAndRemoveInvalidateRangeCacheWithoutDeletingForeignKeys(){String unrelated=settings.getPrefix()+"foreign:sentinel";redis.opsForValue().set(unrelated,"keep",Duration.ofMinutes(1));rates.range(1L,arrival,arrival.plusDays(2));
        rates.save(1L,arrival,rate("140"));assertFalse(redis.hasKey(range(1,arrival,arrival.plusDays(2))));assertEquals(new BigDecimal("140.00"),rates.range(1L,arrival,arrival.plusDays(2)).get(0).price());
        rates.save(1L,arrival,rate("150"));assertEquals(new BigDecimal("150.00"),rates.range(1L,arrival,arrival.plusDays(2)).get(0).price());rates.remove(1L,arrival);assertTrue(rates.range(1L,arrival,arrival.plusDays(2)).isEmpty());assertEquals("keep",redis.opsForValue().get(unrelated));
    }
    @Test void rateInvalidationWaitsForCommitAndRollbackPreservesCache(){rates.save(1L,arrival,rate("140"));rates.range(1L,arrival,arrival.plusDays(2));
        new TransactionTemplate(txManager).executeWithoutResult(s->{rates.save(1L,arrival,rate("150"));assertTrue(redis.hasKey(range(1,arrival,arrival.plusDays(2))));s.setRollbackOnly();});
        assertEquals(new BigDecimal("140.00"),rates.range(1L,arrival,arrival.plusDays(2)).get(0).price());
        new TransactionTemplate(txManager).executeWithoutResult(s->{rates.save(1L,arrival,rate("160"));assertTrue(redis.hasKey(range(1,arrival,arrival.plusDays(2))));});assertFalse(redis.hasKey(range(1,arrival,arrival.plusDays(2))));}
    @Test void cachedRateConfigurationIsNeverContractAuthority(){rates.save(1L,arrival,rate("140"));rates.range(1L,arrival,arrival.plusDays(3));jdbc.update("UPDATE room_rate SET price=175 WHERE room_type_id=1 AND rate_date=?",arrival);
        assertEquals(new BigDecimal("140.00"),rates.range(1L,arrival,arrival.plusDays(3)).get(0).price());long b=create();assertEquals(375,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());invariants(b);}
    @ParameterizedTest @ValueSource(booleans={true,false}) void concurrentMissCanRefillOldValueButOnlyUntilTtl(boolean rateCache)throws Exception{
        if(rateCache)rates.save(1L,arrival,rate("140"));var pool=Executors.newSingleThreadExecutor();gate.arm("cache-reader",rateCache?"RoomRateMapper.selectByRoomTypeIdAndDateRange":"RoomTypeMapper.selectById",false);
        try{var read=pool.submit(()->{Thread.currentThread().setName("cache-reader");return rateCache?rates.range(1L,arrival,arrival.plusDays(2)):types.getRoomTypeById(1L);});SqlGate.await(gate.reached);
            if(rateCache)rates.save(1L,arrival,rate("150"));else types.updateRoomType(1L,type("new value"));gate.release.countDown();read.get(10,TimeUnit.SECONDS);
            String key=rateCache?range(1,arrival,arrival.plusDays(2)):detail(1);assertTrue(redis.hasKey(key));
            if(rateCache)assertEquals(new BigDecimal("140.00"),rates.range(1L,arrival,arrival.plusDays(2)).get(0).price());else assertEquals("test_standard",types.getRoomTypeById(1L).getTypeName());
            ttl(key,rateCache?60:1800); // This is eventual consistency, deliberately not a strong-consistency assertion.
            redis.expire(key,Duration.ofMillis(50));org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->!redis.hasKey(key));
            if(rateCache)assertEquals(new BigDecimal("150.00"),rates.range(1L,arrival,arrival.plusDays(2)).get(0).price());else assertEquals("new value",types.getRoomTypeById(1L).getTypeName());
        }finally{gate.clear();pool.shutdown();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}}
    @Test void redisFailureFallsBackAndAfterCommitEvictionCannotTurnCommittedWriteIntoFailure(){types.getRoomTypeById(1L);doThrow(new RedisConnectionFailureException("simulated unavailable")).when(connectionFactory).getConnection();
        try{assertEquals("test_standard",types.getRoomTypeById(1L).getTypeName());assertDoesNotThrow(()->new TransactionTemplate(txManager).executeWithoutResult(s->types.updateRoomType(1L,type("saved"))));assertEquals("saved",jdbc.queryForObject("SELECT type_name FROM room_type WHERE id=1",String.class));}finally{reset(connectionFactory);}}
    @Test void malformedCachedJsonFallsBackToDatabase(){redis.opsForValue().set(detail(1),"{bad json",Duration.ofMinutes(1));assertEquals("test_standard",types.getRoomTypeById(1L).getTypeName());assertEquals(1,counts.get("RoomTypeMapper.selectById"));}

    @Test void configuredShortTtlsActuallyExpireWithoutExplicitEviction(){
        var shortSettings=new HotelRedisProperties();shortSettings.setPrefix(settings.getPrefix()+"short:");
        shortSettings.setDetailTtl(Duration.ofMillis(200));shortSettings.setListTtl(Duration.ofMillis(200));shortSettings.setRateTtl(Duration.ofMillis(200));
        var manager=new CacheConfig().cacheManager(connectionFactory,shortSettings);manager.afterPropertiesSet();
        var detail=manager.getCache(CacheConfig.ROOM_TYPE_DETAIL);var list=manager.getCache(CacheConfig.ROOM_TYPE_LIST);var rate=manager.getCache(CacheConfig.ROOM_RATE_RANGE);
        var value=types.getRoomTypeById(1L);detail.put("1",value);list.put("all",List.of(value));rate.put("range",List.of());
        assertNotNull(detail.get("1"));assertNotNull(list.get("all"));assertNotNull(rate.get("range"));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->detail.get("1")==null&&list.get("all")==null&&rate.get("range")==null);
    }

    @Test void rateDatabaseFailureRollsBackAndPreservesCache(){
        rates.range(1L,arrival,arrival.plusDays(1));gate.arm(Thread.currentThread().getName(),"RoomRateMapper.insert",true);
        assertThrows(Exception.class,()->rates.save(1L,arrival,rate("140")));
        assertTrue(redis.hasKey(range(1,arrival,arrival.plusDays(1))));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM room_rate",Integer.class));
    }

    @Test void rateApiValidationAndRoleBoundaries()throws Exception{
        String url="/api/admin/room-types/1/rates";
        for(String role:new String[]{"STAFF","HR_ADMIN","CUSTOMER"})
            mvc.perform(put(url+"/2026-10-01").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("actor").roles(role))
                    .contentType("application/json").content("{\"price\":140,\"rateSource\":\"MANUAL\"}" )).andExpect(status().isForbidden());
        mvc.perform(put(url+"/2026-10-01").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER"))
                .contentType("application/json").content("{\"price\":1.001,\"rateSource\":\"MANUAL\"}" )).andExpect(status().isBadRequest());
        mvc.perform(put(url+"/2026-10-01").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER"))
                .contentType("application/json").content("{\"price\":140,\"rateSource\":\"MANUAL\"}" )).andExpect(status().isOk());
        mvc.perform(get(url).param("start","2026-10-01").param("end","2026-10-02").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].price").value(140));
        for(String role:new String[]{"HR_ADMIN","CUSTOMER"})mvc.perform(get(url).param("start","2026-10-01").param("end","2026-10-02")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("actor").roles(role))).andExpect(status().isForbidden());
    }

    @Test void concurrentRateUpsertsKeepOneConfigurationRow()throws Exception{
        var pool=Executors.newFixedThreadPool(2);gate.arm("rate-writer","RoomRateMapper.insert",false);
        try{
            var first=pool.submit(()->{Thread.currentThread().setName("rate-writer");return rates.save(1L,arrival,rate("140"));});SqlGate.await(gate.reached);
            var started=new CountDownLatch(1);var second=pool.submit(()->{started.countDown();return rates.save(1L,arrival,rate("150"));});assertTrue(started.await(5,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->second.get(100,TimeUnit.MILLISECONDS));gate.release.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM room_rate WHERE room_type_id=1 AND rate_date=?",Integer.class,arrival));
            assertEquals(new BigDecimal("150.00"),rates.range(1L,arrival,arrival.plusDays(1)).get(0).price());
        }finally{gate.clear();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
