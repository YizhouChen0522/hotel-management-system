package com.johnny.hotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class FolioItemCommand {

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

    private Boolean refundable;
}