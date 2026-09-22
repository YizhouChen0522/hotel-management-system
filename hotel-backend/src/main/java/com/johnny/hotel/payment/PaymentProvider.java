package com.johnny.hotel.payment;

public interface PaymentProvider {
    String id();
    Creation create(PaymentAttempt attempt);
    VerifiedPaymentEvent verifyAndNormalize(RawPaymentWebhook webhook);
    ProviderPaymentResult queryPayment(PaymentAttempt attempt);
    record Creation(String providerPaymentId,String status) {}
}
