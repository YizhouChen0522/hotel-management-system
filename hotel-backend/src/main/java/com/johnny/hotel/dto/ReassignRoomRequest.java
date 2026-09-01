package com.johnny.hotel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReassignRoomRequest {

    @NotNull(message = "Room id cannot be null")
    private Long roomId;
}
