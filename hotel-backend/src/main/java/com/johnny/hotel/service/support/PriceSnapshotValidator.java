package com.johnny.hotel.service.support;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PriceSnapshotValidator {
    private final BookingPriceVersionMapper versions;
    private final BookingNightlyRateMapper nightly;
    public List<BookingNightlyRate> validate(Booking booking, String currency) {
        BookingPriceVersion version = versions.selectActiveByBookingId(booking.getId());
        List<BookingNightlyRate> rates = nightly.selectActiveByBookingId(booking.getId());
        BillingRules.snapshot(booking, version, rates, currency);
        return rates;
    }
}
