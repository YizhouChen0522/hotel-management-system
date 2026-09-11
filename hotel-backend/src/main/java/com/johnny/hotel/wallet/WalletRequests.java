package com.johnny.hotel.wallet;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

public final class WalletRequests {
    private WalletRequests() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopUp {
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2)
        private BigDecimal amount;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}")
        private String requestKey;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Decision {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}")
        private String requestKey;
        @NotBlank @Size(max=255)
        private String reason;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Status {
        @NotNull private WalletStatus status;
        @NotBlank @Size(max=255) private String reason;
    }
}
