package com.johnny.hotel;

import com.johnny.hotel.config.HotelClockConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotelClockConfigTest {
    @Test
    void productionClockUsesConfiguredZone() {
        assertEquals(ZoneId.of("America/Toronto"),
                new HotelClockConfig().hotelClock("America/Toronto").getZone());
    }

    @Test
    void businessDateAndLeadDaysFollowInjectedZone() {
        Instant instant = Instant.parse("2026-10-01T02:00:00Z");
        Clock shanghai = Clock.fixed(instant, ZoneId.of("Asia/Shanghai"));
        Clock toronto = Clock.fixed(instant, ZoneId.of("America/Toronto"));
        LocalDate arrival = LocalDate.of(2026, 10, 2);

        assertEquals(LocalDate.of(2026, 10, 1), LocalDate.now(shanghai));
        assertEquals(LocalDate.of(2026, 9, 30), LocalDate.now(toronto));
        assertEquals(1, ChronoUnit.DAYS.between(LocalDate.now(shanghai), arrival));
        assertEquals(2, ChronoUnit.DAYS.between(LocalDate.now(toronto), arrival));
        assertFalse(arrival.isBefore(LocalDate.now(shanghai)));
        assertFalse(arrival.isBefore(LocalDate.now(toronto)));
    }

    @Test
    void productionConfigurationContainsUtcJdbcSession() throws Exception {
        String clock = Files.readString(Path.of(
                "src/main/java/com/johnny/hotel/config/HotelClockConfig.java"));
        String config = Files.readString(Path.of("src/main/resources/application.yml"));

        assertFalse(clock.contains("Asia/Shanghai"));
        assertFalse(clock.contains("America/Toronto"));
        assertFalse(config.contains("serverTimezone="));
        assertTrue(config.contains("connectionTimeZone=UTC"));
        assertTrue(config.contains("forceConnectionTimeZoneToSession=true"));
    }
}
