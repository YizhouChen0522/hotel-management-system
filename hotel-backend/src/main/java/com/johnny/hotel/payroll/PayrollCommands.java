package com.johnny.hotel.payroll;import lombok.*;import java.math.*;import java.time.*;
public final class PayrollCommands{private PayrollCommands(){}
 @Data public static class CompensationCreate{Long employeeId;String payType,currency,reason;BigDecimal baseAmount,hourlyRate;LocalDate effectiveFrom,effectiveTo;}
 @Data public static class PolicyWrite{String monthlyUnpaidMode,hourlyPaidLeaveMode,roundingMode;BigDecimal standardDays,standardHoursPerDay;}
 @Data public static class PeriodCreate{LocalDateTime periodStart,periodEnd;String currency,requestKey;java.util.List<Long> employeeIds;}
 @Data public static class AdjustmentCreate{String type,direction,reason,requestKey;BigDecimal amount;}
 @Data public static class PaymentConfirm{String method,externalReference,requestKey;}
}
