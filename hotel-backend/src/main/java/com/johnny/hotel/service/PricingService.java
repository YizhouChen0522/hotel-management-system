package com.johnny.hotel.service;

import com.johnny.hotel.dto.pricing.RoomPriceQuote;

import java.time.LocalDate;

public interface PricingService {

    RoomPriceQuote quoteRoomType(
            Long roomTypeId,
            LocalDate checkInDate,
            LocalDate checkOutDate
    );
    java.util.List<com.johnny.hotel.pricing.PricingDecision> explain(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate);
}
