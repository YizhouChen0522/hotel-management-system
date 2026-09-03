package com.johnny.hotel.service;

import com.johnny.hotel.entity.BookingPriceVersion;

public interface BookingPricingService {

    BookingPriceVersion createFullRepriceVersion(
            Long bookingId,
            Long roomTypeId,
            String changeType,
            String reason,
            Long operatorId
    );
    BookingPriceVersion createDateChangeVersion(
            Long bookingId,
            String reason,
            Long operatorId
    );
}
