package com.johnny.hotel.reservation;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReservationNoShow {
    private Long id;
    private Long bookingId;
    private Long operatorUserId;
    private Long policyId;
    private String reason;
    private BigDecimal depositBefore;
    private BigDecimal forfeitedAmount;
    private LocalDateTime createTime;
}
