package com.johnny.hotel.workforce;

import lombok.*;
import java.math.BigDecimal;
import java.time.*;

public final class WorkforceModels {
 private WorkforceModels(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class AttendanceSession {
  private Long id,employeeId,checkInPunchId,checkOutPunchId; private Integer status;
  private LocalDateTime effectiveCheckIn,effectiveCheckOut,createTime,updateTime;
  private Long workedSeconds; private String exceptionCode;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class AttendancePunch {
  private Long id,employeeId,sessionId,operatorUserId; private Integer punchType;
  private LocalDateTime punchedAt,createTime; private String ipAddress,source,userAgent,requestKey;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class AttendanceCorrection {
  private Long id,employeeId,departmentIdSnapshot,approverUserIdSnapshot,sessionId,taskId,decidedBy;
  private String correctionType,reason,requestKey,decisionNote,approvalRoute; private Integer status;
  private LocalDateTime requestedCheckIn,requestedCheckOut,requestedAt,decidedAt,createTime,updateTime;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class LeaveType {
  private Long id,createdBy,updatedBy; private String code,name,unit,paidTreatment,description;
  private Integer requiresBalance,requiresAttachment,active; private LocalDateTime createTime,updateTime;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class LeaveAllocation {
  private Long id,employeeId,leaveTypeId,createdBy; private LocalDate periodStart,periodEnd;
  private BigDecimal amount; private String reason,requestKey; private LocalDateTime createTime;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class LeaveRequest {
  private Long id,employeeId,departmentIdSnapshot,approverUserIdSnapshot,leaveTypeId,taskId,decidedBy;
  private LocalDateTime startAt,endAt,submittedAt,decidedAt,cancelledAt,createTime,updateTime;
  private BigDecimal requestedAmount; private String reason,attachmentReference,requestKey,decisionNote,cancelReason,approvalRoute;
  private Integer status;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class LeaveBalance {
  private Long employeeId,leaveTypeId; private BigDecimal allocated,pending,approved,available;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class CorrectionFact {
  private Long id,requestId,sessionId,approvedBy; private LocalDateTime effectiveCheckIn,effectiveCheckOut,approvedAt,createTime;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class ApprovalConfig {
  private Integer id; private Long hrDepartmentId,updatedBy; private LocalDateTime updateTime;
 }
}
