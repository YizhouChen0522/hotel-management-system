package com.johnny.hotel.payment;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CheckoutNightlyQuote {
    private Long id;
    private Long checkoutSessionId;
    private LocalDate stayDate;
    private Long roomTypeId;
    private BigDecimal rateAmount;
    private String rateSource;
    private Long dynamicPolicyId;
    private Long manualOverrideId;
}
