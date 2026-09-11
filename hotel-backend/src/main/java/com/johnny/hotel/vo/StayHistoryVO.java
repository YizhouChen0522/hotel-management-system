package com.johnny.hotel.vo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StayHistoryVO {
    private Long id;
    private Long userId;
    private Long folioId;
    private LocalDateTime actualCheckInTime;
    private LocalDateTime actualCheckOutTime;
    private String roomNumber;
    private String roomTypeName;
}
