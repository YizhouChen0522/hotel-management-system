package com.johnny.hotel.entity;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomTurnoverTask {
    private Long id,taskId,roomId,stayId,assignmentId,acceptedBy,completedBy;
    private Integer status;
    private LocalDateTime acceptedTime,completedTime,createTime,updateTime;
    private String note;
}
