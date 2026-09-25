package com.johnny.hotel.settlement;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SettlementReconciliationAttempt {
    private Long id;
    private Long batchId;
    private Integer attemptNo;
    private String requestKey;
    private String status;
    private BigDecimal calculatedGross;
    private BigDecimal calculatedFee;
    private BigDecimal calculatedNet;
    private String exceptionReason;
    private Long performedBy;
    private LocalDateTime createTime;
}
