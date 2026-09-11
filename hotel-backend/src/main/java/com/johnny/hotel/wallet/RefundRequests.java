package com.johnny.hotel.wallet;
import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class RefundRequests {
    private RefundRequests() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Create {
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal amount;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
        @NotBlank @Size(max=255) private String reason;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Process {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
        @NotBlank @Size(max=255) private String reason;
    }
}
