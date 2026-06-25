package com.johnny.hotel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class Room {
    private Long id;
    private String roomNumber;
    private Long roomTypeId;
    private Integer floor;
    private Integer status; // 0: disabled, 1：available, 2: booking, 3: under maintenance，4：occupied
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
