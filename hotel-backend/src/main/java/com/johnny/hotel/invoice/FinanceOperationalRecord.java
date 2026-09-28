package com.johnny.hotel.invoice;
import lombok.*;import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class FinanceOperationalRecord {private String domain,status,label;private Long id,bookingId,stayId,roomId,taskId,userId;private LocalDateTime occurredAt,updatedAt;}
