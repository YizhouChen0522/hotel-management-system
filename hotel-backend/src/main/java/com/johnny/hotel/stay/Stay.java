package com.johnny.hotel.stay;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Stay {
    private Long id;
    private Long bookingId;
    private Long primaryGuestId;
    private Long registrationId;
    private Integer status;
    private LocalDateTime actualCheckInTime;
    private LocalDateTime actualCheckOutTime;
    private Long checkedInBy;
    private Long checkedOutBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
