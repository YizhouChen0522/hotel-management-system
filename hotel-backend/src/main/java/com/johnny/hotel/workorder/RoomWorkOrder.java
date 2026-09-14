package com.johnny.hotel.workorder;
import lombok.*;import java.math.BigDecimal;import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class RoomWorkOrder {
 private Long id,taskId,roomId,reportedBy;private String requestKey,damageType,description;private Integer severity,status;private Boolean affectsSellability,blocksRoomRelease;private BigDecimal estimatedCost,actualCost;private LocalDateTime resolvedTime,cancelledTime,createTime,updateTime;
}
