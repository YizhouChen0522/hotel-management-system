package com.johnny.hotel.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data
public class RoomRequest {
    @NotBlank(message = "Room number cannot be blank")
    private String roomNumber;

    @NotNull(message = "Room type id cannot be null")
    private Long roomTypeId;

    @NotNull(message = "Floor cannot be null")
    @Min(value = 1, message = "Floor must be at least 1")
    private Integer floor;
}
