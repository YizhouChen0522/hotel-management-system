package com.johnny.hotel.payment;

import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PaymentRecoveryClaimService {
    private final PaymentCoreMapper db;private final PaymentRecoveryProperties config;private final SysAuditLogMapper audits;private final Clock clock;
    public List<Long> candidates(){LocalDateTime now=LocalDateTime.now(clock);return db.recoveryCandidates(now,now.minus(config.getPendingQueryAfter()),config.getBatchSize());}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public String claim(Long id){LocalDateTime now=LocalDateTime.now(clock);String token=UUID.randomUUID().toString();return db.claimRecovery(id,token,now,now.plus(config.getLeaseDuration()),now.minus(config.getPendingQueryAfter()))==1?token:null;}
    public PaymentAttempt claimed(Long id,String token){return db.claimed(id,token);}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void success(Long id,String token){var attempt=db.lockClaimed(id,token);if(attempt==null)return;db.recoverySucceeded(id,token,LocalDateTime.now(clock));audits.insert(SysAuditLog.builder().operatorId(attempt.getInitiatedByUserId()).targetUserId(attempt.getInitiatedByUserId()).action("PAYMENT_AUTOMATIC_RECOVERY_COMPLETED").detail("Attempt "+id+" recovery completed").build());}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void retry(Long id,String token,String message){
        var attempt=db.lockClaimed(id,token);if(attempt==null)return;LocalDateTime now=LocalDateTime.now(clock);int retry=(attempt.getRecoveryRetryCount()==null?0:attempt.getRecoveryRetryCount())+1;
        db.recoveryFailed(id,token,now,now.plus(config.backoff(retry)),safe(message));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void manualReview(Long id,String token,String message){
        var attempt=db.lockClaimed(id,token);if(attempt==null)return;String safe=safe(message);db.recoveryManualReview(id,token,LocalDateTime.now(clock),safe);
        audits.insert(SysAuditLog.builder().operatorId(attempt.getInitiatedByUserId()).targetUserId(attempt.getInitiatedByUserId()).action("PAYMENT_RECOVERY_MANUAL_REVIEW").detail("Attempt "+id+": "+safe).build());
    }
    private String safe(String value){String s=value==null?"Temporary payment recovery failure":value.replaceAll("[\\r\\n\\t]"," ");return s.substring(0,Math.min(255,s.length()));}
}
