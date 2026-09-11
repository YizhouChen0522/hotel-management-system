package com.johnny.hotel.entity;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolioItem {

    private String eventKey;

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
    private Long stayAdjustmentId;

    private Integer refundable;

    private Long createdBy;

    private LocalDateTime createTime;
}
