package com.johnny.hotel.payment;

import com.johnny.hotel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class PaymentAttemptService {
    private final PaymentCoreMapper db;
    private final PaymentProviderRegistry providers;
    private final Clock clock;
    @Transactional
    public PaymentAttempt create(Long checkoutId,PaymentRequests.Attempt request,Long customerId){
        var checkout=db.lockCheckout(checkoutId);
        if(checkout==null||!customerId.equals(checkout.getCustomerUserId()))throw new BusinessException(404,"Checkout session not found");
        var old=db.attemptByKey(checkoutId,request.getRequestKey());
        String provider=request.getProvider().trim().toUpperCase();
        if(old!=null){require(old.getProvider().equals(provider),"Attempt request key already used for another provider");return old;}
        require("OPEN".equals(checkout.getStatus()),"Checkout session cannot start another payment");
        require(!LocalDateTime.now(clock).isAfter(checkout.getExpiresAt()),"Checkout session has expired");
        var adapter=providers.require(provider);
        var attempt=PaymentAttempt.builder().checkoutSessionId(checkoutId).purpose("RESERVATION_DEPOSIT").provider(provider)
                .requestKey(request.getRequestKey()).merchantPaymentNo("HPM"+UUID.randomUUID().toString().replace("-",""))
                .amount(checkout.getQuotedTotal()).currency(checkout.getCurrency()).status("CREATED").fulfillmentStatus("PENDING")
                .initiatorType("CUSTOMER").initiatedByUserId(customerId).build();
        one(db.insertAttempt(attempt));
        var creation=adapter.create(attempt);
        attempt.setProviderPaymentId(creation.providerPaymentId());attempt.setStatus(creation.status());
        // MOCK accepts the request into PENDING; no customer call can move it to success.
        if(!"PENDING".equals(creation.status()))throw new BusinessException("Provider returned an unsupported initial state");
        one(db.initializeAttempt(attempt.getId(),creation.status(),creation.providerPaymentId()));
        one(db.checkoutStatus(checkoutId,"OPEN","PAYMENT_PENDING"));
        return db.lockAttempt(attempt.getId());
    }
    public PaymentAttempt get(Long id,Long customerId){var a=db.attempt(id);if(a==null)throw new BusinessException(404,"Payment attempt not found");var c=db.checkout(a.getCheckoutSessionId());if(c==null||!customerId.equals(c.getCustomerUserId()))throw new BusinessException(404,"Payment attempt not found");return a;}
}
