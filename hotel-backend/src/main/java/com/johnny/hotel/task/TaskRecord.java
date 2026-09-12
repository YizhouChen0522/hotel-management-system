package com.johnny.hotel.task;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskRecord { private Long id,taskId,assignmentId,actorUserId; private Integer recordType; private String detail; private LocalDateTime createTime; }
