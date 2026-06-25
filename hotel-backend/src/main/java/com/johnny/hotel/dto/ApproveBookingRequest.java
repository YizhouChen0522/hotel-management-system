package com.johnny.hotel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveBookingRequest {

    @NotNull(message = "Assigned room id cannot be null")
    private Long assignedRoomId;
}