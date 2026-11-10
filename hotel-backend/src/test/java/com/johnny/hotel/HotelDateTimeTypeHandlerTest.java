package com.johnny.hotel;

import com.johnny.hotel.config.HotelDateTimeTypeHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotelDateTimeTypeHandlerTest {
    private static final ZoneId HOTEL_ZONE = ZoneId.of("America/New_York");

    @Test
    void uniqueHotelTimeRoundTripsThroughUtcStorage() {
        var handler = handler("2026-11-09T17:00:00Z");
        LocalDateTime hotelTime = LocalDateTime.of(2026, 11, 9, 12, 0);

        assertEquals(LocalDateTime.of(2026, 11, 9, 17, 0), handler.toUtc(hotelTime));
        assertEquals(hotelTime, handler.fromUtc(handler.toUtc(hotelTime)));
    }

    @Test
    void overlapUsesTheActualClockOccurrence() {
        LocalDateTime repeatedHotelTime = LocalDateTime.of(2026, 11, 1, 1, 30);

        assertEquals(LocalDateTime.of(2026, 11, 1, 5, 30),
                handler("2026-11-01T05:30:00Z").toUtc(repeatedHotelTime));
        assertEquals(LocalDateTime.of(2026, 11, 1, 6, 30),
                handler("2026-11-01T06:30:00Z").toUtc(repeatedHotelTime));
        assertThrows(IllegalArgumentException.class,
                () -> handler("2026-11-09T17:00:00Z").toUtc(repeatedHotelTime));
    }

    @Test
    void daylightSavingGapIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> handler("2026-03-08T07:30:00Z")
                        .toUtc(LocalDateTime.of(2026, 3, 8, 2, 30)));
    }

    private HotelDateTimeTypeHandler handler(String instant) {
        return new HotelDateTimeTypeHandler(
                Clock.fixed(Instant.parse(instant), HOTEL_ZONE));
    }
}
