package com.johnny.hotel.cashier;import lombok.*;import jakarta.validation.constraints.*;import java.math.*;import java.time.*;
public final class CashierModels{private CashierModels(){}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Drawer{Long id,activeShiftId,createdBy;String code,name,currency,status;LocalDateTime createTime,updateTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Shift{Long id,drawerId,operatorUserId;String status;BigDecimal openingCount,expectedClosing,closingCount,closingVariance;LocalDateTime openedAt,closedAt;LocalDate openingBusinessDate,closingBusinessDate;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Movement{Long id,drawerId,shiftId,sourceId,operatorUserId;String direction,movementType,currency,sourceType,requestKey,note;BigDecimal amount;LocalDateTime occurredAt;LocalDate postingBusinessDate;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Variance{Long id,shiftId,recordedBy,reviewedBy;String varianceType,status,reason,resolutionNote;BigDecimal expectedAmount,countedAmount,differenceAmount;LocalDateTime reviewedAt,createTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Handover{Long id,drawerId,previousShiftId,nextShiftId,recordedBy;String status;BigDecimal previousClosingCount,nextOpeningCount,differenceAmount;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Transfer{Long id,drawerId,shiftId,taskId,requestedBy,confirmedBy;String requestType,currency,status,requestKey,note;BigDecimal amount;LocalDateTime confirmedAt,createTime;}
 public record CreateDrawer(@NotBlank@Size(max=50)String code,@NotBlank@Size(max=120)String name,@NotBlank@Pattern(regexp="[A-Z]{3}")String currency){}
 public record Count(@NotNull@DecimalMin("0.00")@Digits(integer=10,fraction=2)BigDecimal amount,@Size(max=500)String reason){}
 public record TransferRequest(@NotNull@DecimalMin("0.01")@Digits(integer=10,fraction=2)BigDecimal amount,@NotNull String type,@NotBlank@Size(max=100)String requestKey,@Size(max=500)String note){}
 public record ShiftView(Shift shift,BigDecimal expected,Variance variance,Handover handover){}
}
