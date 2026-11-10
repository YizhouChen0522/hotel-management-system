package com.johnny.hotel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class HotelClockConfig {
    private static final Logger log = LoggerFactory.getLogger(HotelClockConfig.class);

    @Bean
    public Clock hotelClock(@Value("${hotel.time.zone-id}") String zoneId) {
        Clock clock = Clock.system(ZoneId.of(zoneId));
        log.info("Hotel timezone: {}; current time: {}", clock.getZone(), java.time.ZonedDateTime.now(clock));
        return clock;
    }
}
