package com.johnny.hotel.ar;
import lombok.*;import java.math.*;import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ArAccount {private Long id,createdBy;private String accountType,name,status,currency,contactName,contactEmail,externalReference;private BigDecimal creditLimit;private Integer paymentTermsDays;private LocalDateTime createTime,updateTime;}
