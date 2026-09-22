package com.johnny.hotel.payment;

import com.johnny.hotel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockPaymentProvider implements PaymentProvider {
    private final String secret;
    private final ObjectMapper json;
    private final Clock clock;
    private final ConcurrentHashMap<String,Object> queryResults=new ConcurrentHashMap<>();
    public MockPaymentProvider(@Value("${hotel.payments.mock.webhook-secret:}") String secret,ObjectMapper json,Clock clock){this.secret=secret;this.json=json;this.clock=clock;}
    public String id(){return "MOCK";}
    public Creation create(PaymentAttempt attempt){configured();return new Creation(null,"PENDING");}
    public ProviderPaymentResult queryPayment(PaymentAttempt attempt){
        configured();Object value=queryResults.get(attempt.getMerchantPaymentNo());
        if(value instanceof RuntimeException failure)throw failure;
        if(value instanceof ProviderPaymentResult result)return result;
        return new ProviderPaymentResult(id(),attempt.getMerchantPaymentNo(),attempt.getProviderPaymentId(),attempt.getStatus(),attempt.getAmount(),attempt.getCurrency(),LocalDateTime.now(clock));
    }
    /** Test protocol control; it is an in-process MOCK adapter facility, not an HTTP API. */
    public void setQueryResult(String merchantPaymentNo,ProviderPaymentResult result){queryResults.put(merchantPaymentNo,result);}
    public void setQueryFailure(String merchantPaymentNo,RuntimeException failure){queryResults.put(merchantPaymentNo,failure);}
    public void clearQueryResult(String merchantPaymentNo){queryResults.remove(merchantPaymentNo);}
    public VerifiedPaymentEvent verifyAndNormalize(RawPaymentWebhook webhook){
        configured();
        MockPayload p;
        try{p=json.readValue(webhook.body(),MockPayload.class);}catch(Exception e){throw new BusinessException("Invalid MOCK webhook payload");}
        validate(p);
        String canonical=canonical(p);
        String expected=hmac(canonical);
        String signature=webhook.firstHeader("X-Payment-Signature");
        if(signature==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII)))
            throw new BusinessException(401,"Invalid payment webhook signature");
        return new VerifiedPaymentEvent(id(),p.providerEventId,p.eventType,p.merchantPaymentNo,p.providerPaymentId,
                p.status.trim().toUpperCase(),p.amount,p.currency.trim().toUpperCase(),p.occurredAt==null?LocalDateTime.now(clock):p.occurredAt,sha256(new String(webhook.body(),StandardCharsets.UTF_8)));
    }
    private String canonical(MockPayload p){return String.join("|",p.providerEventId,p.eventType,p.merchantPaymentNo,p.providerPaymentId,p.status.trim().toUpperCase(),p.amount.toPlainString(),p.currency.trim().toUpperCase());}
    private void validate(MockPayload p){if(p==null||blank(p.providerEventId)||blank(p.eventType)||blank(p.merchantPaymentNo)||blank(p.providerPaymentId)||blank(p.status)||p.amount==null||p.amount.signum()<=0||blank(p.currency))throw new BusinessException("Invalid MOCK webhook payload");}
    private boolean blank(String s){return s==null||s.isBlank();}
    private void configured(){if(secret==null||secret.length()<16)throw new BusinessException(503,"MOCK payment provider is not configured");}
    private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    public static final class MockPayload {
        public String providerEventId;public String eventType;public String merchantPaymentNo;public String providerPaymentId;
        public String status;public BigDecimal amount;public String currency;public LocalDateTime occurredAt;
        public MockPayload(){}
    }
}
