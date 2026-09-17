package com.johnny.hotel.booking.deposit;

import lombok.*;
import java.time.LocalDateTime;

/** Reservation liability account. It is neither a Stay nor a Folio. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DepositAccount {
    private Long id;
    private Long bookingId;
    private String currency;
    private LocalDateTime createTime;
}
