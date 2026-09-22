package com.johnny.hotel.payment;

import com.johnny.hotel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class PaymentRecognitionService {
    private final PaymentCoreMapper db;
    private final SysAuditLogMapper audits;
    private final com.johnny.hotel.businessdate.BusinessDateService businessDates;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long recognize(VerifiedPaymentEvent event){
        String provider=event.provider();
        one(db.ensureEvent(provider,event.providerEventId(),event.eventType(),event.providerPaymentId(),event.payloadHash()));
        require(event.payloadHash().equals(db.lockEventHash(provider,event.providerEventId())),"Webhook event id was reused with another payload");
        var attempt=db.lockAttemptByMerchant(event.merchantPaymentNo());
        Long result=apply(attempt,event.result());
        one(db.eventStatus(provider,event.providerEventId(),"PROCESSED"));
        return result;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long recognizeQuery(Long attemptId,String claimToken,ProviderPaymentResult result){
        var attempt=db.lockClaimed(attemptId,claimToken);
        require(attempt!=null,"Payment recovery claim is no longer valid");
        Long value=apply(attempt,result);
        if("SUCCEEDED".equals(result.status()))audit(attempt,"PAYMENT_PROVIDER_QUERY_SUCCEEDED","Provider query confirmed successful payment");
        return value;
    }
    private Long apply(PaymentAttempt attempt,ProviderPaymentResult event){
        mismatch(attempt!=null&&event.provider().equals(attempt.getProvider()),"Payment attempt mapping is invalid");
        mismatch(attempt.getMerchantPaymentNo().equals(event.merchantPaymentNo()),"Merchant payment number mismatch");
        mismatch(attempt.getAmount().compareTo(event.amount())==0,"Payment amount mismatch");
        mismatch(attempt.getCurrency().equals(event.currency()),"Payment currency mismatch");
        mismatch(attempt.getProviderPaymentId()==null||event.providerPaymentId()==null||attempt.getProviderPaymentId().equals(event.providerPaymentId()),"Provider payment id mismatch");
        if("SUCCEEDED".equals(event.status())){
            if(!"SUCCEEDED".equals(attempt.getStatus())) one(db.succeed(attempt.getId(),event.providerPaymentId(),businessDates.postingDate()));
            return attempt.getId();
        }
        if(java.util.Set.of("FAILED","CANCELLED","EXPIRED").contains(event.status())){
            if(!"SUCCEEDED".equals(attempt.getStatus())) db.applyNonSuccess(attempt.getId(),event.providerPaymentId(),event.status(),event.occurredAt());
            return null;
        }
        if(java.util.Set.of("PENDING","PROCESSING","REQUIRES_ACTION").contains(event.status())){
            if(!"SUCCEEDED".equals(attempt.getStatus()))db.applyNonSuccess(attempt.getId(),event.providerPaymentId(),event.status(),event.occurredAt());
            return null;
        }
        throw new BusinessException("Unsupported provider payment state");
    }
    private void mismatch(boolean valid,String message){if(!valid)throw new ProviderResultMismatchException(message);}
    private void audit(PaymentAttempt attempt,String action,String detail){one(audits.insert(SysAuditLog.builder().operatorId(attempt.getInitiatedByUserId()).targetUserId(attempt.getInitiatedByUserId()).action(action).detail("Attempt "+attempt.getId()+": "+detail).build()));}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void markRetryable(Long id,Exception failure){
        String message=failure.getMessage()==null?"Local fulfillment failed":failure.getMessage();
        db.retryable(id,message.substring(0,Math.min(255,message.length())));
    }
}
