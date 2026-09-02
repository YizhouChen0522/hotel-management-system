package com.johnny.hotel.dto.pricing;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class NightlyRate {

    private LocalDate stayDate;

    private Long roomTypeId;

    private BigDecimal price;

    private String rateSource;
}
