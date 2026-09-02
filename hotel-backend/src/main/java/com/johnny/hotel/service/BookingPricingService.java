package com.johnny.hotel.service;

import com.johnny.hotel.entity.BookingPriceVersion;

public interface BookingPricingService {

    BookingPriceVersion createPriceVersion(
            Long bookingId,
            Long roomTypeId,
            String changeType,
            String reason,
            Long operatorId
    );
}
