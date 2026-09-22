package com.johnny.hotel.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Provider-neutral event. Instances are returned only after adapter authenticity verification. */
public record VerifiedPaymentEvent(String provider,String providerEventId,String eventType,String merchantPaymentNo,
        String providerPaymentId,String status,BigDecimal amount,String currency,LocalDateTime occurredAt,String payloadHash) {
    public ProviderPaymentResult result(){return new ProviderPaymentResult(provider,merchantPaymentNo,providerPaymentId,status,amount,currency,occurredAt);}
}
