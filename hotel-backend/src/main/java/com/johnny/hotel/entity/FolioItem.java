package com.johnny.hotel.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FolioItem {

    private Long id;

    private Long folioId;

    private String itemType;

    private String description;

    private LocalDate businessDate;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Long roomId;

    private Long roomTypeId;

    private Long roomAssignmentId;

    private Long sourceItemId;

    private Integer refundable;

    private Long createdBy;

    private LocalDateTime createTime;
}
