package com.johnny.hotel.dto;
import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomRateRequest {
    @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) private BigDecimal price;
    @NotBlank @Size(max=30) private String rateSource;
    @Size(max=255) private String description;
}
