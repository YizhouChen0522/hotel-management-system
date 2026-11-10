package com.johnny.hotel.businessdate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HotelTimeAuthority {
    private final Clock clock;

    public Instant instant() {
        return clock.instant();
    }

    public ZoneId zoneId() {
        return clock.getZone();
    }

    public LocalDate hotelDate() {
        return LocalDate.now(clock);
    }

    public ZonedDateTime hotelDateTime() {
        return ZonedDateTime.now(clock);
    }
}
