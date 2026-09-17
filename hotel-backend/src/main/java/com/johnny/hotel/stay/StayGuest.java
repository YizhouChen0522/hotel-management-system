package com.johnny.hotel.stay;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StayGuest {
    private Long id;
    private Long stayId;
    private Long guestId;
    private Integer guestRole;
    private Long registeredBy;
    private LocalDateTime createTime;
}
