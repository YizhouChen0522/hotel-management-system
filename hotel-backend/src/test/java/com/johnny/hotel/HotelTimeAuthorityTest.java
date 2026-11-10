package com.johnny.hotel;

import com.johnny.hotel.businessdate.HotelTimeAuthority;
import com.johnny.hotel.config.HotelClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HotelTimeAuthorityTest {
    private static final ZoneId HOTEL_ZONE = ZoneId.of("America/New_York");

    @Test
    void configuredZoneDoesNotFollowJvmDefault() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(HOTEL_ZONE, new HotelClockConfig().hotelClock("America/New_York").getZone());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void fixedInstantUsesHotelCalendarAcrossUtcMidnight() {
        var time = new HotelTimeAuthority(Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), HOTEL_ZONE));
        assertEquals(LocalDate.of(2026, 10, 27), time.hotelDate());
        assertEquals(Instant.parse("2026-10-28T02:00:00Z"), time.instant());
    }

    @Test
    void daylightSavingBoundariesUseZoneRules() {
        var beforeSpring = new HotelTimeAuthority(Clock.fixed(Instant.parse("2026-03-08T06:59:00Z"), HOTEL_ZONE));
        var afterSpring = new HotelTimeAuthority(Clock.fixed(Instant.parse("2026-03-08T07:01:00Z"), HOTEL_ZONE));
        assertEquals(1, beforeSpring.hotelDateTime().getHour());
        assertEquals(3, afterSpring.hotelDateTime().getHour());
        var beforeFall = new HotelTimeAuthority(Clock.fixed(Instant.parse("2026-11-01T05:59:00Z"), HOTEL_ZONE));
        var afterFall = new HotelTimeAuthority(Clock.fixed(Instant.parse("2026-11-01T06:01:00Z"), HOTEL_ZONE));
        assertEquals(1, beforeFall.hotelDateTime().getHour());
        assertEquals(1, afterFall.hotelDateTime().getHour());
        assertEquals(-4 * 3600, beforeFall.hotelDateTime().getOffset().getTotalSeconds());
        assertEquals(-5 * 3600, afterFall.hotelDateTime().getOffset().getTotalSeconds());
    }
}
