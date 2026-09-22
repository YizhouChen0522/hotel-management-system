package com.johnny.hotel.reservation;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.enums.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.guest.GuestAccess;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.stay.StayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class ReservationLifecycleService {
    private final BookingMapper bookings;
    private final RoomMapper rooms;
    private final StayMapper stays;
    private final DepositMapper deposits;
    private final ReservationLifecycleMapper facts;
    private final ReservationPolicyService policies;
    private final GuestAccess access;
    private final SysAuditLogMapper audits;
    private final Clock clock;
    private final com.johnny.hotel.businessdate.BusinessDateService businessDates;

    private String reason(String value){require(value!=null&&!value.isBlank()&&value.length()<=500,"Reason is required (500 characters maximum)");return value.trim();}
    private void hotel(Long actor){access.employee(actor);}
    private DepositLedgerRules.Balance balance(DepositAccount a){return DepositLedgerRules.balance(
            deposits.payments(a.getId()),deposits.refunds(a.getId()),deposits.transfers(a.getId()),deposits.settlements(a.getId()));}
    private DepositAccount account(Booking b){var value=deposits.byBooking(b.getId());return value==null?null:deposits.lock(value.getId());}
    private void settle(DepositAccount a,Booking b,String kind,BigDecimal amount,Long actor,String key,LocalDate businessDate){
        if(amount.signum()==0)return;
        one(deposits.insertSettlement(DepositSettlement.builder().accountId(a.getId()).bookingId(b.getId()).kind(kind)
                .amount(amount).status("FORFEIT".equals(kind)?1:0).eventKey(key).createdBy(actor).businessDate("FORFEIT".equals(kind)?businessDate:null).build()));
    }
    private void release(Booking b){
        if(b.getStatus()!=BookingStatus.APPROVED.getCode())return;
        var room=rooms.selectByIdForUpdate(b.getReservedRoomId());require(room!=null,"Reserved room is missing");
        if(room.getStatus()==RoomStatus.BOOKED.getCode()&&!bookings.hasOtherApprovedReservation(room.getId(),b.getId()))
            one(rooms.transitionStatus(room.getId(),RoomStatus.BOOKED.getCode(),RoomStatus.AVAILABLE.getCode()));
    }
    private void audit(Booking b,Long actor,String action){one(audits.insert(SysAuditLog.builder().operatorId(actor)
            .targetUserId(b.getUserId()).action(action).detail("Reservation "+b.getId()).build()));}

    @Transactional public ReservationCancellation cancel(Long bookingId,Long actor,String initiator,String explanation){
        var roles=access.activeRoles(actor);
        require(Set.of("CUSTOMER","HOTEL").contains(initiator),"Invalid cancellation initiator");
        var b=bookings.selectByIdForUpdate(bookingId);if(b==null)throw new BusinessException(404,"Reservation not found");
        boolean customer=roles.equals(Set.of("CUSTOMER"));
        if(customer){if(!actor.equals(b.getUserId()))throw new BusinessException(404,"Reservation not found");require("CUSTOMER".equals(initiator),"Customer cannot act for the hotel");}
        else hotel(actor);
        var prior=facts.cancellation(bookingId);
        if(prior!=null){require(prior.getInitiator().equals(initiator),"Cancellation was already recorded with another initiator");return prior;}
        require(b.getStatus()==BookingStatus.PENDING.getCode()||b.getStatus()==BookingStatus.APPROVED.getCode(),"Only pending or approved reservations can be cancelled");
        require(stays.lockByBooking(bookingId)==null,"Reservation already converted to a Stay");
        String why=reason(explanation);
        release(b);
        var a=account(b);
        var money=a==null?null:balance(a);
        BigDecimal before=money==null?BigDecimal.ZERO.setScale(2):money.balance();
        BigDecimal available=money==null?BigDecimal.ZERO.setScale(2):money.available();
        int lead=(int)Math.max(0,ChronoUnit.DAYS.between(LocalDate.now(clock),b.getCheckInDate()));
        BigDecimal percent=null, refund=BigDecimal.ZERO.setScale(2);
        if("HOTEL".equals(initiator))refund=available;
        else if(b.getReservationPolicyId()!=null){
            percent=policies.percent(b.getReservationPolicyId(),lead);
            var target=(money==null?BigDecimal.ZERO:money.received()).multiply(percent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            BigDecimal already=money==null?BigDecimal.ZERO:money.refunded().add(money.externalRefunded()).add(money.pendingRefund());
            refund=target.subtract(already).max(BigDecimal.ZERO).min(available).setScale(2);
        }else require(available.signum()==0,"Legacy reservation with deposit requires an explicit cancellation policy decision");
        var forfeit=available.subtract(refund).setScale(2);
        var businessDate=businessDates.postingDate();
        if(a!=null){settle(a,b,"REFUND",refund,actor,"cancel-refund:"+bookingId,businessDate);settle(a,b,"FORFEIT",forfeit,actor,"cancel-forfeit:"+bookingId,businessDate);}
        one(bookings.transitionStatus(bookingId,b.getStatus(),BookingStatus.CANCELLED.getCode()));
        var fact=ReservationCancellation.builder().bookingId(bookingId).initiator(initiator).operatorUserId(actor).reason(why)
                .policyId(b.getReservationPolicyId()).leadDays(lead).refundPercent(percent).depositBefore(before)
                .refundObligation(refund).forfeitedAmount(forfeit).build();
        one(facts.insertCancellation(fact));audit(b,actor,"CANCEL_RESERVATION");return facts.cancellation(bookingId);
    }

    @Transactional public ReservationNoShow noShow(Long bookingId,Long operator,String note){
        hotel(operator);
        var b=bookings.selectByIdForUpdate(bookingId);if(b==null)throw new BusinessException(404,"Reservation not found");
        var prior=facts.noShow(bookingId);if(prior!=null)return prior;
        require(b.getStatus()==BookingStatus.APPROVED.getCode()&&b.getCheckInDate().isBefore(LocalDate.now(clock)),
                "No-show requires an expired approved arrival");
        require(stays.lockByBooking(bookingId)==null,"Reservation already converted to a Stay");
        String why=reason(note);
        release(b);
        var a=account(b);var money=a==null?null:balance(a);
        require(money==null||money.pendingRefund().signum()==0,"Resolve pending deposit refunds before no-show");
        var before=money==null?BigDecimal.ZERO.setScale(2):money.balance();
        var forfeit=money==null?BigDecimal.ZERO.setScale(2):money.available();
        var businessDate=businessDates.postingDate();
        if(a!=null)settle(a,b,"FORFEIT",forfeit,operator,"no-show-forfeit:"+bookingId,businessDate);
        one(bookings.transitionStatus(bookingId,b.getStatus(),BookingStatus.NO_SHOW.getCode()));
        var fact=ReservationNoShow.builder().bookingId(bookingId).operatorUserId(operator).policyId(b.getReservationPolicyId())
                .reason(why).depositBefore(before).forfeitedAmount(forfeit).build();
        one(facts.insertNoShow(fact));audit(b,operator,"MARK_NO_SHOW");return facts.noShow(bookingId);
    }

    @Transactional public DepositSettlement confirmExternalRefund(Long bookingId,Long settlementId,Long actor,String externalReference){
        var roles=access.activeRoles(actor);
        require(!roles.contains("HR_ADMIN")&&!roles.contains("CUSTOMER")&&roles.stream().anyMatch(Set.of("FINANCE","MANAGER","OWNER","SUPER_ADMIN")::contains),"Refund confirmation requires finance authority");
        String ref=reason(externalReference);require(ref.length()<=100,"External reference is too long");
        var b=bookings.selectByIdForUpdate(bookingId);require(b!=null,"Reservation not found");
        var a=account(b);require(a!=null,"Deposit account not found");
        var s=deposits.lockSettlement(settlementId);
        require(s!=null&&s.getAccountId().equals(a.getId())&&"REFUND".equals(s.getKind()),"Refund obligation not found");
        if(s.getStatus()==1){require(ref.equals(s.getExternalReference()),"External refund was already confirmed differently");return s;}
        require(!actor.equals(s.getCreatedBy()),"Cannot confirm your own refund obligation");
        balance(a);
        var businessDate=businessDates.postingDate();
        one(deposits.completeExternalRefund(s.getId(),actor,ref,businessDate));audit(b,actor,"DEPOSIT_EXTERNAL_REFUND_SUCCESS");return deposits.settlement(s.getId());
    }
}
