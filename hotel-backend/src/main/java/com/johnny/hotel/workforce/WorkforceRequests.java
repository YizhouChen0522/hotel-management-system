package com.johnny.hotel.workforce;
import lombok.*;import java.math.BigDecimal;import java.time.*;
public final class WorkforceRequests {private WorkforceRequests(){}
 @Data public static class CorrectionCreate {private Long sessionId;private String correctionType,reason,requestKey;private LocalDateTime requestedCheckIn,requestedCheckOut;}
 @Data public static class Decision {private String note;}
 @Data public static class LeaveTypeWrite {private String code,name,unit,paidTreatment,description;private Boolean requiresBalance,requiresAttachment,active;}
 @Data public static class AllocationCreate {private Long employeeId,leaveTypeId;private LocalDate periodStart,periodEnd;private BigDecimal amount;private String reason,requestKey;}
 @Data public static class LeaveCreate {private Long leaveTypeId;private LocalDateTime startAt,endAt;private BigDecimal requestedAmount;private String reason,attachmentReference,requestKey;}
 @Data public static class Cancel {private String reason;}
 @Data public static class Reassign {private Long approverUserId;private String reason;}
 @Data public static class ApprovalConfigWrite {private Long hrDepartmentId;}
}
