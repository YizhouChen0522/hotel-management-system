package com.johnny.hotel.payroll;
import lombok.*;import java.math.*;import java.time.*;
public final class PayrollModels{private PayrollModels(){}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Compensation{Long id,employeeId,createdBy,approvedBy;String payType,currency,reason;BigDecimal baseAmount,hourlyRate;LocalDate effectiveFrom,effectiveTo;Integer status;LocalDateTime approvedAt,createTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Policy{Long id,createdBy,activatedBy;Integer versionNo;String status,monthlyUnpaidMode,hourlyPaidLeaveMode,roundingMode;BigDecimal standardDays,standardHoursPerDay;LocalDateTime activatedAt,createTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Period{Long id,policyId,createdBy,approvedBy;LocalDateTime periodStart,periodEnd,approvedAt,paidAt,closedAt,createTime,updateTime;String currency,requestKey;Integer status;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Payslip{Long id,payrollPeriodId,employeeId,compensationProfileId,expenseId;Integer status,attendanceSessionCount,attendanceAnomalyCount,correctionFactCount;Boolean sourceChangeDetected;String blockedReason,payType,currency,leaveSnapshot,policySnapshot;BigDecimal rateSnapshot,paidLeaveAmount,unpaidLeaveAmount,basePay,attendanceBasedPay,paidLeavePay,unpaidLeaveDeduction,allowances,bonuses,manualDeductions,otherAdjustments,grossPay,totalDeductions,netPay;Long workedMinutes;LocalDateTime calculatedAt,approvedAt,paidAt,createTime,updateTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Adjustment{Long id,payslipId,createdBy;String adjustmentType,direction,reason,requestKey;BigDecimal amount;LocalDateTime createTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Payment{Long id,payslipId,paidBy,expenseId;BigDecimal amount;String paymentMethod,externalReference,requestKey;LocalDate postingBusinessDate;LocalDateTime paidAt,createTime;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class LeaveInput{String unit,paidTreatment;BigDecimal amount;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class AttendanceInput{Long workedMinutes;Integer sessionCount,anomalyCount,correctionCount;}
}
