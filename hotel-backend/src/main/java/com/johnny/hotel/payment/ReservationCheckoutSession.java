package com.johnny.hotel.payment;

import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReservationCheckoutSession {
    private Long id;
    private Long customerUserId;
    private Long roomTypeId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer guestCount;
    private String currency;
    private BigDecimal quotedTotal;
    private Long reservationPolicyId;
    private String requestKey;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
