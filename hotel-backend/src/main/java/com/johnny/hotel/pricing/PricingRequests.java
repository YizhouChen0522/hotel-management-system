package com.johnny.hotel.pricing; import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.List;
public final class PricingRequests{private PricingRequests(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class PolicySave{@NotBlank private String name;@NotNull @DecimalMin("0.01") private BigDecimal minimumMultiplier;@NotNull @DecimalMin("0.01") private BigDecimal maximumMultiplier;@Valid @NotEmpty private List<Occupancy> occupancyBands;@Valid @NotEmpty private List<Window> bookingWindowBands;@Valid @NotNull private List<Cell> cells;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Occupancy{@NotBlank private String label;@NotNull private Integer lowerInclusive;@NotNull private Integer upperExclusive;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Window{@NotBlank private String label;@NotNull private Integer minDays;private Integer maxDays;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Cell{@NotNull private Integer occupancyIndex;@NotNull private Integer windowIndex;@NotNull private BigDecimal adjustmentPercent;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Override{@NotNull private Long roomTypeId;@NotNull private LocalDate startDate;@NotNull private LocalDate endDate;@NotBlank private String overrideType;@NotNull private BigDecimal value;@NotBlank private String reason;}
}
