package com.johnny.hotel.reservation;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReservationPolicyBand {
    private Long id;
    private Long policyId;
    private Integer minLeadDays;
    private Integer maxLeadDays;
    private BigDecimal refundPercent;
}
