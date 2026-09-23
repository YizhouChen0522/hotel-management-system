package com.johnny.hotel.ar;
import lombok.*;import java.math.*;import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ArLedgerEntry {private Long id,accountId,folioId,sourceId,operatorUserId;private String entryType,currency,sourceType,requestKey,reason,paymentMethod,externalReference;private BigDecimal amount;private LocalDate postingBusinessDate;private LocalDateTime occurredAt,createTime;}
