package com.johnny.hotel.pricing; import lombok.*;import java.math.BigDecimal;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DynamicPricingCell{private Long id,policyId,occupancyBandId,bookingWindowBandId;private BigDecimal adjustmentPercent;}
