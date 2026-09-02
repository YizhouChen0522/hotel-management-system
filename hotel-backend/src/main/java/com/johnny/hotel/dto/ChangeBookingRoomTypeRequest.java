package com.johnny.hotel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeBookingRoomTypeRequest {

    @NotNull(message = "New room type id cannot be null")
    private Long newRoomTypeId;

    @NotNull(message = "New room id cannot be null")
    private Long newRoomId;

    @Size(max = 200, message = "Reason cannot exceed 200 characters")
    private String reason;
}
