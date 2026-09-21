package com.johnny.hotel.reservation;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReservationPolicy {
    private Long id;
    private Long versionNo;
    private String name;
    private Integer status;
    private Long createdBy;
    private Long activatedBy;
    private LocalDateTime createTime;
    private LocalDateTime activatedTime;
}
