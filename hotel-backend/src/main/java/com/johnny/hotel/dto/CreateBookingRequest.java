package com.johnny.hotel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateBookingRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
    private String requestKey;
    @NotNull(message = "Room type ID cannot be null")
    private Long roomTypeId;

    @NotNull(message = "Please input the number of guests")
    @Min(value = 1, message = "Customer number must be at least 1")
    private Integer guestCount;

    @NotNull(message = "Please input check-in date")
    private LocalDate checkInDate;

    @NotNull(message = "Please input check-out date")
    private LocalDate checkOutDate;
}
