package com.johnny.hotel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private Long id;

    private Long folioId;

    private BigDecimal amount;

    private String paymentMethod;

    private String status;

    private String referenceNo;

    private String note;

    private Long createdBy;

    private LocalDateTime paidTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
