package com.johnny.hotel.dto;
import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterExpenseRequest {
    @NotBlank @Size(max=36) private String idempotencyKey;
    @NotBlank @Pattern(regexp="SERVICE_CHARGE|DAMAGE_CHARGE|DISCOUNT|FEE_REVERSAL") private String itemType;
    @NotNull @Digits(integer=10,fraction=2) private BigDecimal amount;
    @NotNull private LocalDate businessDate;
    @NotBlank @Size(max=500) private String description;
    @NotBlank @Size(max=500) private String reason;
    @Positive private Long sourceExpenseId;
}
