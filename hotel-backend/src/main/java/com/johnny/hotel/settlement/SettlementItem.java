package com.johnny.hotel.settlement;import lombok.*;import java.math.*;import java.time.*;
@Data@Builder@NoArgsConstructor@AllArgsConstructor public class SettlementItem{private Long id,batchId,sourceId;private String sourceType,providerPaymentId,direction,currency,status,exceptionReason;private BigDecimal grossAmount,feeAmount,netAmount;private LocalDateTime providerOccurredAt,createTime;}
