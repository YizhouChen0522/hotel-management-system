package com.johnny.hotel.stay;

import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.wallet.RefundAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.*;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class StayAdjustmentService {
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Value("${hotel.currency}") private String hotelCurrency;
    private final StayTimes times;
    private final StayAdjustmentMapper adjustments;
    private final BookingMapper bookings;
    private final RoomMapper rooms;
    private final BookingRoomAssignmentMapper assignments;
    private final FolioMapper folios;
    private final FolioItemMapper items;
    private final FolioService ledger;
    private final StayPricing pricing;
    private final RoomConflictReader conflicts;
    private final RefundAccess access;
    private final SysAuditLogMapper audits;
    private Long actor(){Long id=access.actor();access.operational(id,false);return id;}
    @Transactional
    public List<StayAdjustment> list(Long bookingId){actor();require(bookings.selectByIdForUpdate(bookingId)!=null,"Booking does not exist");return adjustments.forBooking(bookingId);}
    @Transactional
    public StayAdjustment apply(Long bookingId,StayAdjustmentRequest request) {
        Long actor=actor();require(request!=null&&request.getType()!=null,"Adjustment type is required");
        require(request.getRequestKey()!=null&&request.getRequestKey().matches("[A-Za-z0-9_-]{8,64}"),"Invalid request key");
        require(request.getReason()!=null&&!request.getReason().isBlank()&&request.getReason().length()<=255,"Reason is required (255 characters maximum)");
        var booking=bookings.selectByIdForUpdate(bookingId);require(booking!=null,"Booking does not exist");
        var history=adjustments.forBooking(bookingId);
        var retry=history.stream().filter(a->a.getRequestKey().equals(request.getRequestKey())).findFirst().orElse(null);
        if(retry!=null){require(retry.getAdjustmentType()==request.getType().getCode()&&retry.getReason().equals(request.getReason().trim())&&(request.getType()!=StayAdjustmentType.EXTENSION?request.getNewEnd()==null:Objects.equals(retry.getNewEnd(),request.getNewEnd())),"Request key already used for another adjustment");return retry;}
        require(booking.getStatus()==2,"Stay adjustments require a checked-in booking");
        var room=rooms.selectByIdForUpdate(booking.getAssignedRoomId());
        var assignment=assignments.selectActiveByBookingId(bookingId);
        require(room!=null&&room.getStatus()==4&&assignment!=null&&assignment.getRoomId().equals(room.getId())&&assignment.getRoomTypeId().equals(room.getRoomTypeId()),"Current room or assignment is inconsistent");
        var folio=folios.selectByBookingIdForUpdate(bookingId);require(folio!=null&&folio.getClosedTime()==null,"Folio is finalized");
        var posted=items.selectByFolioIdForUpdate(folio.getId());
        var now=LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);var today=now.toLocalDate();
        var end=StayPlan.end(booking,history);
        require(!now.isBefore(assignment.getStartTime())&&!today.isBefore(booking.getCheckInDate()),"Adjustment precedes actual stay");
        require(history.stream().noneMatch(a->a.getAdjustmentType()==2),"Early departure already processed");
        var a=StayAdjustment.builder().bookingId(bookingId).folioId(folio.getId()).assignmentId(assignment.getId()).operatorId(actor)
                .adjustmentType(request.getType().getCode()).oldEnd(end).newEnd(end).effectiveTime(now).requestKey(request.getRequestKey()).reason(request.getReason().trim()).build();
        var commands=new ArrayList<FolioItemCommand>();
        var previous=activeLate(history);
        if(request.getType()==StayAdjustmentType.EXTENSION) {
            require(request.getNewEnd()!=null&&request.getNewEnd().isAfter(end)&&!request.getNewEnd().isBefore(today),"Extension must add nights through at least the current business date");
            require(ChronoUnit.DAYS.between(end,request.getNewEnd())<=365,"An extension is limited to 365 nights");
            var collisions=conflicts.overlapping(room.getId(),bookingId,end,request.getNewEnd());
            require(collisions.isEmpty(),"Extension conflicts with a future reservation; explicitly change room first");
            a.setNewEnd(request.getNewEnd());a.setPreviousLateId(previous==null?null:previous.getId());
            require(folio.getCurrency().equals(hotelCurrency),"Current rate currency differs from Folio currency");
            var quote=pricing.quote(room.getRoomTypeId(),end,a.getNewEnd());
            one(adjustments.insert(a));
            for(var n:quote.getNightlyRates()) {
                one(adjustments.insertNight(ExtensionNightlyRate.builder().adjustmentId(a.getId()).bookingId(bookingId).roomTypeId(room.getRoomTypeId()).stayDate(n.getStayDate()).rateAmount(n.getPrice()).rateSource(n.getRateSource()).currency(folio.getCurrency()).build()));
                commands.add(charge(a,assignment,"ROOM_CHARGE",n.getStayDate(),n.getPrice(),null));
            }
            reverseLate(a,previous,posted,commands);
        } else if(request.getType()==StayAdjustmentType.EARLY_CHECKOUT) {
            require(request.getNewEnd()==null&&today.isBefore(booking.getCheckOutDate())&&today.isBefore(end),"Early checkout must precede the original planned departure");
            a.setNewEnd(today);one(adjustments.insert(a));
            var unused=posted.stream().filter(i->"ROOM_CHARGE".equals(i.getItemType())&&!i.getBusinessDate().isBefore(today)&&posted.stream().noneMatch(c->i.getId().equals(c.getSourceItemId()))).toList();
            require(unused.size()==ChronoUnit.DAYS.between(today,end),"Unused nightly room charges are incomplete");
            for(var source:unused)commands.add(reverse(a,source,"EARLY_CHECKOUT_REVERSAL"));
        } else {
            require(request.getNewEnd()==null&&today.equals(end),"Late checkout is only supported on the effective departure day");
            require(now.toLocalTime().isBefore(times.getLateCutoff()),"Late cutoff reached; explicit Extension and an additional night are required");
            var arrivals=conflicts.arriving(room.getId(),bookingId,today);
            boolean conflict=!now.toLocalTime().isBefore(times.getNormalCheckin())&&!arrivals.isEmpty();
            if(now.toLocalTime().isAfter(times.freeUntil())) {
                if(conflict) {require(folio.getCurrency().equals(hotelCurrency),"Current rate currency differs from Folio currency");a.setConflictBookingId(arrivals.get(0).getId());var n=pricing.quote(room.getRoomTypeId(),today,today.plusDays(1)).getNightlyRates().get(0);a.setLockedRate(n.getPrice());a.setRateSource(n.getRateSource());}
                else {var source=lastNight(posted,end);a.setBasisItemId(source.getId());a.setLockedRate(source.getAmount());a.setRateSource("LOCKED_NIGHT");}
            }
            if(previous!=null)require(!Objects.equals(previous.getConflictBookingId(),a.getConflictBookingId())||(previous.getLockedRate()==null)!=(a.getLockedRate()==null)||!previous.getAssignmentId().equals(a.getAssignmentId()),"Late checkout is already recorded; reuse its request key");
            a.setPreviousLateId(previous==null?null:previous.getId());one(adjustments.insert(a));
            reverseLate(a,previous,posted,commands);
            if(a.getLockedRate()!=null)commands.add(charge(a,assignment,conflict?"LATE_CHECKOUT_CONFLICT_FEE":"LATE_CHECKOUT_FEE",today,lateAmount(a),null));
        }
        if(!commands.isEmpty())ledger.addItems(bookingId,commands,actor);
        one(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(booking.getUserId()).action("STAY_"+request.getType().name())
                .detail("Booking "+bookingId+", adjustment "+a.getId()+", effective end "+a.getOldEnd()+" -> "+a.getNewEnd()+", reason: "+a.getReason()+(a.getConflictBookingId()==null?"":"; conflicting booking "+a.getConflictBookingId()+" requires manual relocation / Room Change")).build()));
        return adjustments.forBooking(bookingId).stream().filter(r->r.getId().equals(a.getId())).findFirst().orElseThrow();
    }
    static StayAdjustment activeLate(List<StayAdjustment> history){StayAdjustment last=null;for(var r:history){if(r.getAdjustmentType()==3)last=r;else if(r.getAdjustmentType()==1)last=null;}return last;}
    static FolioItem lastNight(List<FolioItem> posted,LocalDate end){var rows=posted.stream().filter(i->"ROOM_CHARGE".equals(i.getItemType())&&end.minusDays(1).equals(i.getBusinessDate())&&posted.stream().noneMatch(c->i.getId().equals(c.getSourceItemId()))).toList();require(rows.size()==1,"Last effective locked night is missing or duplicated");return rows.get(0);}
    static BigDecimal lateAmount(StayAdjustment a){return money(a.getLockedRate().multiply(a.getConflictBookingId()==null?new BigDecimal("0.5"):BigDecimal.ONE).setScale(2,RoundingMode.HALF_UP),12);}
    private FolioItemCommand charge(StayAdjustment a,BookingRoomAssignment segment,String type,LocalDate date,BigDecimal amount,Long source){return FolioItemCommand.builder().itemType(type).businessDate(date).description(type+" for "+date).quantity(BigDecimal.ONE).unitPrice(amount).amount(amount).roomId(segment.getRoomId()).roomTypeId(segment.getRoomTypeId()).roomAssignmentId(segment.getId()).sourceItemId(source).stayAdjustmentId(a.getId()).eventKey("STAY:"+a.getId()+":"+type+":"+date).refundable(true).build();}
    private FolioItemCommand reverse(StayAdjustment a,FolioItem source,String type){return FolioItemCommand.builder().itemType(type).businessDate(source.getBusinessDate()).description(type+" of item "+source.getId()).quantity(BigDecimal.ONE).unitPrice(source.getAmount().negate()).amount(source.getAmount().negate()).roomId(source.getRoomId()).roomTypeId(source.getRoomTypeId()).roomAssignmentId(source.getRoomAssignmentId()).sourceItemId(source.getId()).stayAdjustmentId(a.getId()).eventKey("STAY:"+a.getId()+":REV:"+source.getId()).refundable(true).build();}
    private void reverseLate(StayAdjustment a,StayAdjustment previous,List<FolioItem> posted,List<FolioItemCommand> commands){if(previous==null)return;for(var i:posted)if(previous.getId().equals(i.getStayAdjustmentId())&&Set.of("LATE_CHECKOUT_FEE","LATE_CHECKOUT_CONFLICT_FEE").contains(i.getItemType()))commands.add(reverse(a,i,"STAY_FEE_REVERSAL"));}
}
