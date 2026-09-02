package com.johnny.hotel.dto.pricing;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class RoomPriceQuote {

    private Long roomTypeId;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    private Integer nights;

    private List<NightlyRate> nightlyRates;

    private BigDecimal totalPrice;
}
