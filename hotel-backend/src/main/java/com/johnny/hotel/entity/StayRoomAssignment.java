package com.johnny.hotel.entity;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StayRoomAssignment {

    private Long id;

    private Long stayId;

    /** Derived through Stay; never persisted in this table. */
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
