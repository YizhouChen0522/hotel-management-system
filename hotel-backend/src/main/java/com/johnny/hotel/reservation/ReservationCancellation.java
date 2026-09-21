package com.johnny.hotel.reservation;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReservationCancellation {
    private Long id;
    private Long bookingId;
    private String initiator;
    private Long operatorUserId;
    private String reason;
    private Long policyId;
    private Integer leadDays;
    private BigDecimal refundPercent;
    private BigDecimal depositBefore;
    private BigDecimal refundObligation;
    private BigDecimal forfeitedAmount;
    private LocalDateTime createTime;
}
