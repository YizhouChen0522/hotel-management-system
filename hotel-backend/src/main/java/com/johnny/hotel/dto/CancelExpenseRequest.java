package com.johnny.hotel.dto;
import lombok.*;
import jakarta.validation.constraints.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CancelExpenseRequest {
    @NotBlank @Size(max=36) private String idempotencyKey;
    @NotBlank @Size(max=500) private String reason;
}
