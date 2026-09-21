package com.johnny.hotel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class HotelClockConfig {
    private static final Logger log = LoggerFactory.getLogger(HotelClockConfig.class);
    @Bean
    public Clock hotelClock() {
        Clock clock=Clock.systemDefaultZone();
        log.info("Hotel/Application timezone: {}; current time: {}",clock.getZone(),java.time.ZonedDateTime.now(clock));
        return clock;
    }
}
