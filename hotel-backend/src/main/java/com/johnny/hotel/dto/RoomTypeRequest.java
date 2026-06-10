package com.johnny.hotel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoomTypeRequest {
    @NotBlank(message = "Room type name cannot be blank.")
    private String typeName;

    private String description;

    @NotNull(message = "You must provide a base price for the room type.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    @NotNull(message = "Capacity cannot be null.")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;
}
