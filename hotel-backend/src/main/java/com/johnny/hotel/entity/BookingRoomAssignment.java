package com.johnny.hotel.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BookingRoomAssignment {

    private Long id;

    private Long bookingId;

    private Long roomId;

    private Long roomTypeId;

    private String assignmentType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String changeReason;

    private Long createdBy;

    private LocalDateTime createTime;
}
