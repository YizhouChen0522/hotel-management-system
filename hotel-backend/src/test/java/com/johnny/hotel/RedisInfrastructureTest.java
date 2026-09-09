package com.johnny.hotel;

import com.johnny.hotel.config.HotelRedisProperties;
import com.johnny.hotel.dto.RoomRateRequest;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.RoomRateMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.service.RoomRateService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisInfrastructureTest {
    final RoomRateMapper rates=mock(RoomRateMapper.class);
    final RoomTypeMapper types=mock(RoomTypeMapper.class);
    final RoomRateService service=new RoomRateService(rates,types);
    final LocalDate day=LocalDate.of(2026,10,1);
    RoomRateRequest request(String price){return RoomRateRequest.builder().price(new BigDecimal(price)).rateSource("MANUAL").build();}

    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","100000000"})
    void invalidMoneyCannotReachDatabase(String amount){assertThrows(BusinessException.class,()->service.save(1L,day,request(amount)));verifyNoInteractions(rates,types);}

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","1234567890123456789012345678901"})
    void invalidSourceCannotReachDatabase(String source){var r=request("100");r.setRateSource(source);assertThrows(BusinessException.class,()->service.save(1L,day,r));verifyNoInteractions(rates,types);}

    @Test void invalidDatesAndOwnershipRejected(){
        assertThrows(BusinessException.class,()->service.range(1L,day,day));
        assertThrows(BusinessException.class,()->service.range(1L,null,day));
        assertThrows(BusinessException.class,()->service.range(0L,day,day.plusDays(1)));
        assertThrows(BusinessException.class,()->service.save(1L,null,request("100")));
        verifyNoInteractions(rates,types);
        assertThrows(BusinessException.class,()->service.save(99L,day,request("100")));verifyNoInteractions(rates);
    }

    @Test void rejectedDatabaseWriteDoesNotReportSuccess(){when(types.selectByIdForUpdate(1L)).thenReturn(new RoomType());assertThrows(BusinessException.class,()->service.save(1L,day,request("100")));}

    @Test void configurationAndRequestValidationRejectUnsafeValues(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();var settings=new HotelRedisProperties();assertTrue(validator.validate(settings).isEmpty());
            assertEquals(Duration.ofMinutes(30),settings.getDetailTtl());assertEquals(Duration.ofMinutes(10),settings.getListTtl());assertEquals(Duration.ofSeconds(60),settings.getRateTtl());
            settings.setPrefix("*:");settings.setAuthWindow(Duration.ZERO);settings.setLoginLimit(0);assertEquals(3,validator.validate(settings).size());
            assertFalse(validator.validate(request("1.001")).isEmpty());
            var r=request("100");r.setDescription("x".repeat(256));assertFalse(validator.validate(r).isEmpty());
        }
    }
}
