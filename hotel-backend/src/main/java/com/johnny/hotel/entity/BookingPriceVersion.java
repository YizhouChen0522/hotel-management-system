package com.johnny.hotel.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingPriceVersion {

    private Long id;

    private Long bookingId;

    private Integer versionNo;

    private String changeType;

    private String reason;

    private Integer isActive;

    private BigDecimal totalPrice;

    private String currency;

    private Long createdBy;

    private LocalDateTime createTime;
}
