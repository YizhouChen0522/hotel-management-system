package com.johnny.hotel.nightaudit;
import com.johnny.hotel.businessdate.BusinessDateService;import com.johnny.hotel.exception.BusinessException;import com.johnny.hotel.guest.GuestAccess;import com.johnny.hotel.payment.ReservationPaymentFulfillmentService;import com.johnny.hotel.reservation.ReservationLifecycleService;import com.johnny.hotel.finance.RevenueRecognitionService;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
@Service @RequiredArgsConstructor
public class NightAuditService {
 private final NightAuditMapper db;private final NightAuditStateService state;private final BusinessDateService dates;private final ReservationPaymentFulfillmentService fulfillment;private final ReservationLifecycleService reservations;private final GuestAccess access;private final RevenueRecognitionService revenue;
 public record View(NightAuditRun run,List<NightAuditBlocker> blockers){}
 public record Precheck(java.time.LocalDate businessDate,String businessDateState,List<NightAuditBlocker> blockers,int recoverablePayments,int eligibleNoShows){}
 public View execute(String requestKey,Long actor){manager(actor);var run=state.begin(requestKey,actor);if(Set.of("COMPLETED","BLOCKED").contains(run.getStatus()))return view(run);String token=state.claim(run.getId());if(token==null)return view(db.byDate(run.getBusinessDate()));state.prepare(run.getId(),token);
  try{
   state.step(run.getId(),token,"PAYMENT_RECOVERY");for(Long id:db.incompletePayments(run.getBusinessDate()))try{fulfillment.fulfill(id);}catch(Exception e){state.addBlocker(run.getId(),"PAYMENT_FULFILLMENT","PAYMENT_ATTEMPT",id,e.getMessage());}
   for(Long id:db.incompletePayments(run.getBusinessDate()))state.addBlocker(run.getId(),"PAYMENT_FULFILLMENT","PAYMENT_ATTEMPT",id,"Successful payment fulfillment remains incomplete");
   state.step(run.getId(),token,"ARRIVALS");for(Long id:db.pendingArrivals(run.getBusinessDate()))state.addBlocker(run.getId(),"PENDING_ARRIVAL","BOOKING",id,"Arrival is still pending hotel approval");
   for(Long id:db.approvedArrivals(run.getBusinessDate()))try{reservations.noShowForNightAudit(id,actor,"Night Audit no-show for "+run.getBusinessDate(),run.getBusinessDate());}catch(Exception e){state.addBlocker(run.getId(),"NO_SHOW","BOOKING",id,e.getMessage());}
   if(!db.blockers(run.getId()).isEmpty())return view(state.block(run.getId(),token));state.step(run.getId(),token,"REVENUE_RECOGNITION");revenue.recognize(run.getBusinessDate());state.step(run.getId(),token,"DAILY_SUMMARY");revenue.summarize(run.getBusinessDate(),run.getId());state.step(run.getId(),token,"FINAL_VALIDATION");return view(state.finish(run.getId(),token,actor));
  }catch(Exception failure){return view(state.fail(run.getId(),token,failure.getMessage()));}
 }
 public Precheck precheck(Long actor){read(actor);var c=dates.current();var transientRun=Optional.ofNullable(db.byDate(c.getBusinessDate())).orElse(NightAuditRun.builder().id(-1L).businessDate(c.getBusinessDate()).build());var rows=new ArrayList<NightAuditBlocker>();for(Long id:db.pendingArrivals(c.getBusinessDate()))rows.add(block(transientRun,"PENDING_ARRIVAL","BOOKING",id,"Arrival is still pending hotel approval"));for(Long id:db.overdueStays(c.getBusinessDate()))rows.add(block(transientRun,"OVERDUE_DEPARTURE","STAY",id,"In-house Stay requires explicit resolution"));return new Precheck(c.getBusinessDate(),c.getState(),rows,db.incompletePayments(c.getBusinessDate()).size(),db.approvedArrivals(c.getBusinessDate()).size());}
 public View status(Long actor){read(actor);var run=db.latest();return run==null?new View(null,List.of()):view(run);}
 public List<NightAuditRun> history(Long actor){read(actor);return db.history(100);}
 private View view(NightAuditRun run){return new View(run,run==null?List.of():db.blockers(run.getId()));}
 private NightAuditBlocker block(NightAuditRun r,String type,String object,Long id,String reason){return NightAuditBlocker.builder().runId(r.getId()).blockerType(type).objectType(object).objectId(id).reason(reason).active(1).build();}
 private void manager(Long actor){var roles=access.activeRoles(actor);if(roles.stream().noneMatch(Set.of("MANAGER","OWNER","SUPER_ADMIN")::contains))throw new org.springframework.security.access.AccessDeniedException("Night Audit execution denied");}
 private void read(Long actor){var roles=access.activeRoles(actor);if(roles.stream().noneMatch(Set.of("STAFF","FINANCE","MANAGER","OWNER","SUPER_ADMIN")::contains))throw new org.springframework.security.access.AccessDeniedException("Night Audit access denied");}
}
