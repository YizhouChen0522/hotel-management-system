package com.johnny.hotel.entity;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Folio {

    private java.time.LocalDateTime closedTime;

    private Long id;

    private Long bookingId;

    private Integer status;

    private String currency;

    private BigDecimal totalAmount;

    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;

    private BigDecimal balanceAmount;

    private LocalDateTime settledTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
