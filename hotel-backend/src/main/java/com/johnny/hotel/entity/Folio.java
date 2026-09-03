package com.johnny.hotel.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class Folio {

    private Long id;

    private Long bookingId;

    private String status;

    private String currency;

    private BigDecimal totalAmount;

    private BigDecimal paidAmount;

    private BigDecimal balanceAmount;

    private LocalDateTime settledTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}