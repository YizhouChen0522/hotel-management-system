package com.johnny.hotel.guest;
import lombok.*;import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class BookingGuest {private Long id;private Long bookingId;private Long guestId;private Integer guestRole;private LocalDateTime createTime;private LocalDateTime updateTime;}
