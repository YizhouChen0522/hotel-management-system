package com.johnny.hotel.settlement;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SettlementReconciliationItem {
    private Long id;
    private Long attemptId;
    private Long settlementItemId;
    private String status;
    private String exceptionReason;
    private LocalDateTime createTime;
}
