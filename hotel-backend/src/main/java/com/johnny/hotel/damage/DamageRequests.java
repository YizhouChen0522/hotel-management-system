package com.johnny.hotel.damage;import jakarta.validation.constraints.*;import lombok.*;import java.math.BigDecimal;public final class DamageRequests{private DamageRequests(){}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Report{@NotNull private Long bookingId;@NotNull private Long assignmentId;@NotNull private DamageSourceType sourceType;@NotNull private Long sourceId;@NotBlank@Size(max=500)private String description;@NotBlank@Size(max=80)private String requestKey;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Assess{@NotNull private DamageResponsibility responsibility;@Digits(integer=10,fraction=2)private BigDecimal chargeAmount;@Size(max=500)private String reason;}
}
