package com.johnny.hotel.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomVO {
    private Long id;
    private String roomNumber;
    private Long roomTypeId;
    private String roomTypeName; // Added for easier display
    private Integer floor;
    private Integer status; // 0: disabled, 1: available, 2: occupied, 3: under maintenance
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
