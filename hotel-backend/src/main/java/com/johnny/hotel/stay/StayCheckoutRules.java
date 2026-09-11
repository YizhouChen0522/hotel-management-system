package com.johnny.hotel.stay;
import com.johnny.hotel.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import static com.johnny.hotel.service.support.BillingRules.*;
@Component @RequiredArgsConstructor
public class StayCheckoutRules {
    private final StayTimes times;
    private final RoomConflictReader conflicts;
    public List<FolioItem> validate(Booking booking,Folio folio,List<BookingRoomAssignment> segments,List<StayAdjustment> rows,List<FolioItem> ledger,LocalDateTime now) {
        var end=StayPlan.end(booking,rows);Set<Long> consumed=new HashSet<>();StayAdjustment previousLate=null;boolean early=false;
        for(var a:rows) {
            require(a.getFolioId().equals(folio.getId())&&!early,"Adjustment account or chronology is inconsistent");
            var segment=segments.stream().filter(s->s.getId().equals(a.getAssignmentId())).findFirst().orElse(null);
            require(segment!=null&&!a.getEffectiveTime().isBefore(segment.getStartTime())&&(segment.getEndTime()==null||!a.getEffectiveTime().isAfter(segment.getEndTime())),"Adjustment is outside its actual assignment");
            require(Objects.equals(a.getPreviousLateId(),a.getAdjustmentType()==2?null:previousLate==null?null:previousLate.getId()),"Late adjustment replacement chain is inconsistent");
            if(a.getAdjustmentType()==2){require(a.getNewEnd().isBefore(booking.getCheckOutDate())&&a.getNewEnd().equals(a.getEffectiveTime().toLocalDate()),"Invalid early departure date");early=true;}
            else if(a.getAdjustmentType()==1){require(a.getNewEnd().isAfter(a.getOldEnd()),"Invalid extension dates");}
            else {
                require(a.getAdjustmentType()==3&&a.getNewEnd().equals(a.getOldEnd())&&a.getNewEnd().equals(a.getEffectiveTime().toLocalDate())&&a.getEffectiveTime().toLocalTime().isBefore(times.getLateCutoff()),"Invalid late departure time");
                var fee=ledger.stream().filter(i->a.getId().equals(i.getStayAdjustmentId())&&Set.of("LATE_CHECKOUT_FEE","LATE_CHECKOUT_CONFLICT_FEE").contains(i.getItemType())).toList();
                require(fee.size()==(a.getLockedRate()==null?0:1),"Late fee missing or duplicated");
                if(a.getLockedRate()==null)require(!a.getEffectiveTime().toLocalTime().isAfter(times.freeUntil())&&a.getConflictBookingId()==null&&a.getBasisItemId()==null,"Late fee incorrectly waived");
                else {
                    require(a.getLockedRate().signum()>0&&a.getEffectiveTime().toLocalTime().isAfter(times.freeUntil()),"Invalid late fee pricing");
                    if(a.getConflictBookingId()==null){var basis=ledger.stream().filter(i->i.getId().equals(a.getBasisItemId())).findFirst().orElse(null);require(basis!=null&&"ROOM_CHARGE".equals(basis.getItemType())&&basis.getBusinessDate().equals(a.getOldEnd().minusDays(1))&&basis.getAmount().compareTo(a.getLockedRate())==0,"Late fee locked-night basis mismatch");}
                    else require(!a.getEffectiveTime().toLocalTime().isBefore(times.getNormalCheckin())&&a.getBasisItemId()==null,"Conflict fee before normal arrival time");
                    var item=fee.get(0);require(item.getItemType().equals(a.getConflictBookingId()==null?"LATE_CHECKOUT_FEE":"LATE_CHECKOUT_CONFLICT_FEE")&&item.getAmount().compareTo(StayAdjustmentService.lateAmount(a))==0&&item.getSourceItemId()==null&&item.getBusinessDate().equals(a.getNewEnd()),"Late fee does not match adjustment");
                    attribution(a,segment,item);consumed.add(item.getId());
                }
            }
            if(a.getPreviousLateId()!=null) {
                var old=previousLate;
                var sources=ledger.stream().filter(i->old.getId().equals(i.getStayAdjustmentId())&&Set.of("LATE_CHECKOUT_FEE","LATE_CHECKOUT_CONFLICT_FEE").contains(i.getItemType())).toList();
                var credits=ledger.stream().filter(i->a.getId().equals(i.getStayAdjustmentId())&&"STAY_FEE_REVERSAL".equals(i.getItemType())).toList();
                require(sources.size()==credits.size(),"Superseded late fee must be fully reversed");
                for(var source:sources){var matches=credits.stream().filter(i->source.getId().equals(i.getSourceItemId())).toList();require(matches.size()==1,"Late fee reversal source mismatch");var c=matches.get(0);require(c.getAmount().compareTo(source.getAmount().negate())==0&&Objects.equals(c.getRoomAssignmentId(),source.getRoomAssignmentId())&&c.getBusinessDate().equals(source.getBusinessDate())&&c.getCreatedBy().equals(a.getOperatorId()),"Invalid late fee reversal");consumed.add(c.getId());}
            }
            previousLate=a.getAdjustmentType()==3?a:null;
        }
        require(now.toLocalDate().equals(end),"Explicit Early Checkout or Extension is required for this departure date");
        if(!early) {
            require(now.toLocalTime().isBefore(times.getLateCutoff()),"Late cutoff reached; explicit Extension is required");
            if(now.toLocalTime().isAfter(times.freeUntil())) {
                require(previousLate!=null,"Explicit Late Checkout adjustment is required");
                boolean conflict=!now.toLocalTime().isBefore(times.getNormalCheckin())&&!conflicts.arriving(booking.getAssignedRoomId(),booking.getId(),end).isEmpty();
                require(previousLate.getLockedRate()!=null&&(previousLate.getConflictBookingId()!=null)==conflict,"Late checkout conditions changed; explicitly reassess the adjustment");
            }
        }
        return ledger.stream().filter(i->!consumed.contains(i.getId())).toList();
    }
    private void attribution(StayAdjustment a,BookingRoomAssignment segment,FolioItem i){require(i.getRoomAssignmentId().equals(segment.getId())&&i.getRoomId().equals(segment.getRoomId())&&i.getRoomTypeId().equals(segment.getRoomTypeId())&&i.getCreatedBy().equals(a.getOperatorId())&&i.getQuantity().compareTo(BigDecimal.ONE)==0&&i.getUnitPrice().compareTo(i.getAmount())==0,"Stay fee attribution mismatch");}
}
