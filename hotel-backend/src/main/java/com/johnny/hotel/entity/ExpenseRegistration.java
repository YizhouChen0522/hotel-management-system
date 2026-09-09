package com.johnny.hotel.entity;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpenseRegistration {
    private Long id;
    private Long folioId;
    private String requestKey;
    private String itemType;
    private BigDecimal amount;
    private LocalDate businessDate;
    private String description;
    private String reason;
    private Long sourceExpenseId;
    private String status;
    private Long ledgerItemId;
    private Long registeredBy;
    private LocalDateTime registeredTime;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
    private String cancelKey;
    private String cancelReason;
}
