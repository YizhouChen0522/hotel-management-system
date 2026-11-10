package com.johnny.hotel;

import com.johnny.hotel.config.HotelDateTimeTypeHandler;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HotelBackendApplication.class, properties = {
        "hotel.time.reconciliation.enabled=false",
        "hotel.payment.recovery.enabled=false",
        "spring.cache.type=none",
        "logging.level.root=WARN"})
@EnabledIfSystemProperty(named = "hotel.time.clone.db", matches = "hotel_time_clone_[0-9a-f]{12}")
class UtcDatetimeMappingCloneTest {
    @DynamicPropertySource
    static void cloneProperties(DynamicPropertyRegistry registry) {
        String schema = System.getProperty("hotel.time.clone.db");
        if (!schema.matches("hotel_time_clone_[0-9a-f]{12}")) {
            throw new IllegalStateException("Refusing a non-clone database");
        }
        var settings = WalletDevelopmentGuard.settings();
        registry.add("spring.datasource.url", () -> "jdbc:mysql://localhost:3306/" + schema
                + "?useUnicode=true&characterEncoding=utf-8&connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", () -> settings.get("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> settings.get("DB_PASSWORD"));
        registry.add("spring.data.redis.password", () -> settings.get("REDIS_PASSWORD"));
        registry.add("hotel.time.zone-id", () -> "America/New_York");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BookingMapper bookings;
    @Autowired Clock hotelClock;

    @Test
    void myBatisReturnsHotelTimeFromUtcStorage() {
        assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
        assertEquals("64", jdbc.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success=1 ORDER BY installed_rank DESC LIMIT 1
                """, String.class));
        long id = jdbc.queryForObject("SELECT MIN(id) FROM booking", Long.class);
        LocalDateTime storedUtc = jdbc.queryForObject(
                "SELECT create_time FROM booking WHERE id=?", LocalDateTime.class, id);
        LocalDateTime hotelLocal = bookings.selectById(id).getCreateTime();
        assertEquals(LocalDateTime.ofInstant(
                storedUtc.toInstant(ZoneOffset.UTC), hotelClock.getZone()), hotelLocal);
        assertEquals(storedUtc, new HotelDateTimeTypeHandler(hotelClock).toUtc(hotelLocal));
    }
}
