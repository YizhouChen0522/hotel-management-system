package com.johnny.hotel.finance;

import lombok.*;
import java.math.BigDecimal;
import java.time.*;

public final class FinanceOperationsModels {
 private FinanceOperationsModels(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Dashboard {
  private LocalDate fromBusinessDate; private LocalDate toBusinessDate;
  private BigDecimal roomRevenue; private BigDecimal otherRevenue; private BigDecimal negativeAdjustments;
  private BigDecimal totalRecognizedRevenue; private BigDecimal externalInflow; private BigDecimal externalOutflow;
  private BigDecimal netExternalCashMovement;
  private BigDecimal totalPaidExpense; private BigDecimal netCashOperatingMovement;
  private BigDecimal arOutstanding;
  private String moneyMovementCoverage;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class CategoryExpense {private String category;private BigDecimal amount;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class FinanceException {
  private String sourceType; private Long sourceId; private String exceptionType; private String reason;
  private String status; private LocalDate businessDate; private LocalDateTime occurredAt; private LocalDateTime updatedAt;
 }
}
