package com.johnny.hotel.booking.deposit;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** Immutable receipt: ownership never moves to a Stay Folio. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DepositPayment {
    private Long id;
    private Long accountId;
    private BigDecimal amount;
    private String paymentMethod;
    private String referenceNo;
    private String requestKey;
    private Long receivedBy;
    private LocalDateTime receivedTime;
    private LocalDate businessDate;
}
