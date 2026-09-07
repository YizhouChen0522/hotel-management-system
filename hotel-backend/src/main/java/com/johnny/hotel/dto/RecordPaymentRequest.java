package com.johnny.hotel.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPaymentRequest {

    @NotNull(message = "Payment amount cannot be null")
    @DecimalMin(
            value = "0.01",
            message = "Payment amount must be greater than zero"
    )
    @Digits(
            integer = 10,
            fraction = 2,
            message = "Payment amount must have at most 10 integer digits and 2 decimal places"
    )
    private BigDecimal amount;

    @NotBlank(message = "Payment method cannot be blank")
    private String paymentMethod;

    @Size(
            max = 100,
            message = "Reference number cannot exceed 100 characters"
    )
    private String referenceNo;

    @Size(
            max = 255,
            message = "Payment note cannot exceed 255 characters"
    )
    private String note;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 36, message = "Idempotency key must be a UUID")
    private String idempotencyKey;
}