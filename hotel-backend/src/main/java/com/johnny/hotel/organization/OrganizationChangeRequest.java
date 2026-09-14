package com.johnny.hotel.organization;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizationChangeRequest {
 private Long id,requestedBy,targetUserId,sourceDepartmentId,targetDepartmentId,proposedManagerId,expectedCurrentDepartmentId,expectedManagerId,expectedDepartmentVersion,expectedUserVersion,taskId,processedBy;
 private Integer requestType,status;
 private String requestKey,reason,departmentName,decisionReason;
 private LocalDateTime createTime,processedTime;
}
