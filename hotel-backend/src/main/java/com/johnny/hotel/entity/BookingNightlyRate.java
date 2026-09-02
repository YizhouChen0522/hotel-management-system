package com.johnny.hotel.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingNightlyRate {

    private Long id;

    private Long priceVersionId;

    private Long bookingId;

    private LocalDate stayDate;

    private Long roomTypeId;

    private BigDecimal rateAmount;

    private String rateSource;

    private LocalDateTime createTime;
}
