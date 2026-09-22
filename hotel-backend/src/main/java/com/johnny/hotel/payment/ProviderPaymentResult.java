package com.johnny.hotel.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Provider-neutral result returned only by an authenticated provider adapter. */
public record ProviderPaymentResult(String provider,String merchantPaymentNo,String providerPaymentId,String status,
        BigDecimal amount,String currency,LocalDateTime occurredAt) {}
