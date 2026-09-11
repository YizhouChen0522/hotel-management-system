package com.johnny.hotel.stay;
import lombok.*;
import java.time.LocalDate;
import java.math.BigDecimal;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExtensionNightlyRate {
    private Long id,adjustmentId,bookingId,roomTypeId;
    private LocalDate stayDate;
    private BigDecimal rateAmount;
    private String rateSource,currency;
}
