package com.johnny.hotel.task;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotelTask { private Long id,createdBy,parentTaskId,departmentId; private Integer departmentBranch,departmentRoot,routingSource; private Integer taskType,status,assignmentMode,executionType; private String targetRole,title,description,sourceKey,requestKey; private LocalDateTime completedTime,cancelledTime,createTime,updateTime; }
