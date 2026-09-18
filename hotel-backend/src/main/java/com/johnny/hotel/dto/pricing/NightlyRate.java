package com.johnny.hotel.dto.pricing;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class NightlyRate {

    private LocalDate stayDate;

    private Long roomTypeId;

    private BigDecimal price;

    private String rateSource;

    private Long dynamicPolicyId;
    private Long manualOverrideId;

    public NightlyRate(LocalDate stayDate, Long roomTypeId, BigDecimal price, String rateSource) {
        this(stayDate, roomTypeId, price, rateSource, null, null);
    }
    public NightlyRate(LocalDate stayDate, Long roomTypeId, BigDecimal price, String rateSource,
                       Long dynamicPolicyId, Long manualOverrideId) {
        this.stayDate=stayDate;this.roomTypeId=roomTypeId;this.price=price;this.rateSource=rateSource;
        this.dynamicPolicyId=dynamicPolicyId;this.manualOverrideId=manualOverrideId;
    }
}
