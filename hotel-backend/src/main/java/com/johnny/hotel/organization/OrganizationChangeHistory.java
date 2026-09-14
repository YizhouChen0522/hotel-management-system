package com.johnny.hotel.organization;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizationChangeHistory {
 private Long id,requestId,targetUserId,oldDepartmentId,newDepartmentId,oldManagerId,newManagerId,operatorUserId,approverUserId;
 private Integer actionType;private Boolean bypassApproval;private String reason;private LocalDateTime effectiveTime;
}
