package com.johnny.hotel.task;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Todo {
 private Long id,taskId,assignmentId,userId;
 private Integer status,isActive;
 private LocalDateTime acknowledgedTime,completedTime,endedTime,createTime,updateTime;
}
