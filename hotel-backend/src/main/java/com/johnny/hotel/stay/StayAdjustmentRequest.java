package com.johnny.hotel.stay;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StayAdjustmentRequest {
    @NotNull private StayAdjustmentType type;
    private LocalDate newEnd;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
    @NotBlank @Size(max=255) private String reason;
}
