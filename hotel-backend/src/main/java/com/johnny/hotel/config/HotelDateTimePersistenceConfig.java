package com.johnny.hotel.config;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HotelDateTimePersistenceConfig {
    private final Clock hotelClock;

    @Bean
    ConfigurationCustomizer hotelDateTimeTypeHandler() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(LocalDateTime.class, new HotelDateTimeTypeHandler(hotelClock));
    }
}
