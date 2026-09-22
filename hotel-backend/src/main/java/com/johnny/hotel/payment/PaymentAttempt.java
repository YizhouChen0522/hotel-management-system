package com.johnny.hotel.payment;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentAttempt {
    private Long id;
    private Long checkoutSessionId;
    private String purpose;
    private String provider;
    private String requestKey;
    private String merchantPaymentNo;
    private String providerPaymentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String fulfillmentStatus;
    private String recoveryStatus;
    private Long bookingId;
    private String initiatorType;
    private Long initiatedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String failureCode;
    @JsonIgnore private String failureMessage;
    private Integer recoveryRetryCount;
    private LocalDateTime nextRecoveryAt;
    private LocalDateTime lastRecoveryAt;
    @JsonIgnore private String lastRecoveryError;
    @JsonIgnore private String recoveryClaimToken;
    @JsonIgnore private LocalDateTime recoveryClaimUntil;
    private LocalDate recognizedBusinessDate;
}
