package com.johnny.hotel.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

public final class ReservationPolicyRequests {
    private ReservationPolicyRequests() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Save {
        @NotBlank @Size(max=120) private String name;
        @NotEmpty private List<@Valid Band> bands;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Band {
        @NotNull @Min(0) private Integer minLeadDays;
        @Min(0) private Integer maxLeadDays;
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2)
        private BigDecimal refundPercent;
    }
}
