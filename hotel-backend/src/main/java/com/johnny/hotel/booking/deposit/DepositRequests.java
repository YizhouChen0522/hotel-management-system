package com.johnny.hotel.booking.deposit;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

public final class DepositRequests {
    private DepositRequests() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Receive {
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal amount;
        @NotBlank @Pattern(regexp="CASH|CARD|BANK_TRANSFER") private String paymentMethod;
        @Size(max=100) private String referenceNo;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
    }
}
