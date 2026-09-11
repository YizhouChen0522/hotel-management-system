package com.johnny.hotel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StayHistory {
    private Long id;
    private Long userId;
    private Long folioId;
    private Long assignmentId;
    private LocalDateTime actualCheckInTime;
    private LocalDateTime actualCheckOutTime;
    private String roomNumber;
    private String roomTypeName;
    private LocalDateTime createTime;
}
