package com.johnny.hotel.payment;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotelTransactionRecord {
    private Long id;
    private String direction;
    private BigDecimal amount;
    private String currency;
    private String sourceType;
    private Long sourceId;
    private String businessReferenceType;
    private Long businessReferenceId;
    private String paymentMethod;
    private String channel;
    private String provider;
    private String providerTransactionId;
    private String initiatorType;
    private Long operatorUserId;
    private String description;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDate businessDate;
}
