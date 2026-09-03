package com.johnny.hotel.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class RoomChangeBillingCommand {

    private Long bookingId;

    private Long oldAssignmentId;

    private Long newAssignmentId;

    private Long oldRoomId;

    private Long newRoomId;

    private Long oldRoomTypeId;

    private Long newRoomTypeId;

    private LocalDate changeDate;

    private LocalDate checkOutDate;

    private String reason;

    private Long operatorId;
}
