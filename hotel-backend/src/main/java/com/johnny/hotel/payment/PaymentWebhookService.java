package com.johnny.hotel.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PaymentWebhookService {
    private final PaymentProviderRegistry providers;
    private final PaymentRecognitionService recognition;
    private final ReservationPaymentFulfillmentService fulfillment;
    public void receive(String providerId,RawPaymentWebhook webhook){
        var provider=providers.require(providerId);
        var verified=provider.verifyAndNormalize(webhook);
        Long attemptId=recognition.recognize(verified);
        if(attemptId!=null){try{fulfillment.fulfill(attemptId);}catch(Exception failure){recognition.markRetryable(attemptId,failure);}}
    }
}
