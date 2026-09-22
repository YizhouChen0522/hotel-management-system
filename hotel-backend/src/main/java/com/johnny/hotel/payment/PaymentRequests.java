package com.johnny.hotel.payment;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

public final class PaymentRequests {
    private PaymentRequests() {}
    @Data public static class Checkout {
        @NotNull private Long roomTypeId;
        @NotNull private LocalDate checkInDate;
        @NotNull private LocalDate checkOutDate;
        @NotNull @Min(1) private Integer guestCount;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
    }
    @Data public static class Attempt {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
        @NotBlank private String provider;
    }
}
