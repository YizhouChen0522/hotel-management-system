package com.johnny.hotel.workorder;
import lombok.*;import jakarta.validation.constraints.*;import java.math.BigDecimal;
public final class WorkOrderRequests {private WorkOrderRequests(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Report {
  @NotNull private Long roomId;@NotBlank @Size(max=63) private String requestKey;@NotBlank @Size(max=80) private String damageType;@NotBlank @Size(max=500) private String description;@NotNull private WorkOrderSeverity severity;@NotNull private Boolean blocksRoomRelease;
  /** Legacy field kept for API compatibility; no longer drives any room status change. */
  private Boolean affectsSellability;@DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal estimatedCost;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Complete {@DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal actualCost;@Size(max=500) private String note;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Note {@Size(max=500) private String note;}
}
