package com.johnny.hotel.payment;

import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PaymentRecoveryService {
    private static final Logger log=LoggerFactory.getLogger(PaymentRecoveryService.class);
    private final PaymentRecoveryProperties config;private final PaymentRecoveryClaimService claims;private final PaymentProviderRegistry providers;
    private final PaymentRecognitionService recognition;private final ReservationPaymentFulfillmentService fulfillment;
    public int runOneCycle(){
        if(!config.isEnabled())return 0;int processed=0;
        for(Long id:claims.candidates()){String token=claims.claim(id);if(token==null)continue;processed++;recover(id,token);}
        return processed;
    }
    public void recover(Long id,String token){
        try{
            PaymentAttempt attempt=claims.claimed(id,token);if(attempt==null)return;
            if("SUCCEEDED".equals(attempt.getStatus())){
                fulfillment.fulfill(id);claims.success(id,token);
                log.info("Payment fulfillment recovery completed: attemptId={}, merchantPaymentNo={}, provider={}, retryCount={}",id,attempt.getMerchantPaymentNo(),attempt.getProvider(),attempt.getRecoveryRetryCount());return;
            }
            ProviderPaymentResult result=providers.require(attempt.getProvider()).queryPayment(attempt);
            Long fulfilledAttempt=recognition.recognizeQuery(id,token,result);
            if(fulfilledAttempt!=null){fulfillment.fulfill(fulfilledAttempt);claims.success(id,token);}
            else if(java.util.Set.of("FAILED","CANCELLED","EXPIRED").contains(result.status()))claims.success(id,token);
            else claims.retry(id,token,"Provider payment remains "+result.status());
        }catch(ProviderResultMismatchException mismatch){claims.manualReview(id,token,mismatch.getMessage());log.warn("Payment recovery requires manual review: attemptId={}, reason={}",id,mismatch.getMessage());}
        catch(Exception failure){claims.retry(id,token,"Temporary payment recovery failure ("+failure.getClass().getSimpleName()+")");log.warn("Temporary payment recovery failure: attemptId={}, type={}",id,failure.getClass().getSimpleName());}
    }
}
