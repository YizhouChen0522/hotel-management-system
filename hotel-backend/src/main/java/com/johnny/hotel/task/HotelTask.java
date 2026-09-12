package com.johnny.hotel.task;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotelTask { private Long id,createdBy; private Integer taskType,status,assignmentMode; private String title,description,sourceKey,requestKey; private LocalDateTime completedTime,cancelledTime,createTime,updateTime; }
