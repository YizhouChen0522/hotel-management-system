package com.johnny.hotel.nightaudit;
import com.johnny.hotel.businessdate.*;import com.johnny.hotel.entity.SysAuditLog;import com.johnny.hotel.exception.BusinessException;import com.johnny.hotel.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class NightAuditStateService {
 private final BusinessDateMapper dates;private final NightAuditMapper db;private final SysAuditLogMapper audits;private final Clock clock;
 @Transactional(propagation=Propagation.REQUIRES_NEW) public NightAuditRun begin(String key,Long actor){
  if(key==null||key.isBlank()||key.length()>100)throw new BusinessException("Night Audit request key is required (100 characters maximum)");
  var control=dates.lockExclusive();if(control==null||control.getBusinessDate()==null)throw new BusinessException("Business Date is not initialized");
  var prior=db.byRequest(key);if(prior!=null&&"COMPLETED".equals(prior.getStatus()))return prior;if(prior!=null&&!control.getBusinessDate().equals(prior.getBusinessDate()))throw new BusinessException("Night Audit request key belongs to another Business Date");
  var run=prior!=null?prior:db.byDate(control.getBusinessDate());
  if("OPEN".equals(control.getState())){if(run==null){run=NightAuditRun.builder().businessDate(control.getBusinessDate()).requestKey(key).status("STARTING").lastStep("BEGIN").startedBy(actor).startedAt(LocalDateTime.now(clock)).build();if(db.insert(run)!=1)throw new BusinessException("Night Audit run could not be created");}
   var at=LocalDateTime.now(clock);db.resolveAll(run.getId(),at);for(Long id:db.pendingArrivals(control.getBusinessDate()))db.blocker(NightAuditBlocker.builder().runId(run.getId()).blockerType("PENDING_ARRIVAL").objectType("BOOKING").objectId(id).reason("Arrival is still pending hotel approval").detectedAt(at).build());for(Long id:db.overdueStays(control.getBusinessDate()))db.blocker(NightAuditBlocker.builder().runId(run.getId()).blockerType("OVERDUE_DEPARTURE").objectType("STAY").objectId(id).reason("In-house Stay requires checkout, extension, or another explicit resolution").detectedAt(at).build());var blockers=db.blockers(run.getId());if(!blockers.isEmpty()){db.precheckBlocked(run.getId(),blockers.size()+" precheck blocker(s)");return db.byDate(control.getBusinessDate());}
   if(db.ready(run.getId())!=1||dates.beginClosing(control.getBusinessDate())!=1)throw new BusinessException("Business Date could not enter CLOSING");run=db.byDate(control.getBusinessDate());}
  else if(!"CLOSING".equals(control.getState())||run==null)throw new BusinessException("Business Date is not available for Night Audit");
  return run;
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public String claim(Long id){var now=LocalDateTime.now(clock);String token=UUID.randomUUID().toString();return db.claim(id,token,now,now.plusMinutes(10))==1?token:null;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void prepare(Long run,String token){var locked=db.lock(run);requireToken(locked,token);db.resolveAll(run,LocalDateTime.now(clock));}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void step(Long run,String token,String step){if(db.step(run,token,step,LocalDateTime.now(clock).plusMinutes(10))!=1)throw new BusinessException("Night Audit processing lease was lost");}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void addBlocker(Long run,String type,String object,Long id,String reason){db.blocker(NightAuditBlocker.builder().runId(run).blockerType(type).objectType(object).objectId(id).reason(safe(reason)).detectedAt(LocalDateTime.now(clock)).build());}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public NightAuditRun block(Long run,String token){var rows=db.blockers(run);String message=rows.isEmpty()?"Night Audit validation failed":rows.size()+" blocking exception(s)";if(db.blocked(run,token,message)!=1)throw new BusinessException("Night Audit processing lease was lost");return db.byDate(db.lock(run).getBusinessDate());}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public NightAuditRun fail(Long run,String token,String error){var locked=db.lock(run);requireToken(locked,token);if(db.failed(run,token,safe(error))!=1)throw new BusinessException("Night Audit processing lease was lost");return db.byDate(locked.getBusinessDate());}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public NightAuditRun finish(Long runId,String token,Long actor){
  var control=dates.lockExclusive();var run=db.lock(runId);requireToken(run,token);if(!run.getBusinessDate().equals(control.getBusinessDate())||!"CLOSING".equals(control.getState()))throw new BusinessException("Business Date changed during Night Audit");
  if(!db.blockers(runId).isEmpty())throw new BusinessException("Night Audit still has blocking exceptions");var next=run.getBusinessDate().plusDays(1);var at=LocalDateTime.now(clock);
  if(dates.advanceHistory(run.getBusinessDate(),next,actor,at)!=1||dates.advance(run.getBusinessDate(),next)!=1||db.complete(runId,token,at)!=1)throw new BusinessException("Night Audit final advance failed");
  if(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(null).action("NIGHT_AUDIT_COMPLETED").detail("Business Date "+run.getBusinessDate()+" advanced to "+next+", run "+runId).build())!=1)throw new BusinessException("Night Audit audit log failed");return db.byRequest(run.getRequestKey());
 }
 private void requireToken(NightAuditRun run,String token){if(run==null||!token.equals(run.getProcessingToken()))throw new BusinessException("Night Audit processing lease was lost");}
 private String safe(String value){String text=value==null?"Unspecified Night Audit failure":value.replaceAll("[\\r\\n\\t]"," ");return text.substring(0,Math.min(500,text.length()));}
}
