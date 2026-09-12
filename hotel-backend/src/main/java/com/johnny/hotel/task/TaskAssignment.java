package com.johnny.hotel.task;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskAssignment { private Long id,taskId,assigneeUserId,assignedBy; private Integer status,assignmentRound,isCurrent; private LocalDateTime acceptedTime,completedTime,endedTime,createTime; }
