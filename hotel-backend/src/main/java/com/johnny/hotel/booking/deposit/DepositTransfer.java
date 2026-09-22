package com.johnny.hotel.booking.deposit;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** One check-in moves the remaining liability once. Source receipts remain in their original account. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DepositTransfer {
    private Long id;
    private Long accountId;
    private Long stayId;
    private Long folioId;
    private Long paymentId;
    private BigDecimal amount;
    private String eventKey;
    private Long transferredBy;
    private LocalDateTime createTime;
    private LocalDate businessDate;
}
