package com.johnny.hotel.booking.deposit;

import com.johnny.hotel.entity.Payment;
import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.stay.StayMapper;
import com.johnny.hotel.guest.GuestAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Called only inside the check-in transaction. A transfer is an internal credit, never a second receipt. */
@Service @RequiredArgsConstructor
public class DepositTransferService {
    private final StayMapper stays;
    private final BookingMapper bookings;
    private final DepositMapper deposits;
    private final FolioMapper folios;
    private final PaymentMapper payments;
    private final GuestAccess access;
    private final SysAuditLogMapper audits;
    private final Clock clock;
    private final com.johnny.hotel.businessdate.BusinessDateService businessDates;

    /** Verify the internal credit without reacquiring an account lock after the Folio lock. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void validateHistory(com.johnny.hotel.entity.Folio folio) {
        var transfer=deposits.byFolio(folio.getId());
        var credits=payments.selectByFolioIdForUpdate(folio.getId()).stream()
                .filter(p->"DEPOSIT_TRANSFER".equals(p.getPaymentMethod())).toList();
        if(transfer==null){require(credits.isEmpty(),"Prepayment credit has no deposit transfer");return;}
        var account=deposits.byBooking(folio.getBookingId());
        require(account!=null && account.getId().equals(transfer.getAccountId())
                && folio.getStayId().equals(transfer.getStayId()) && account.getCurrency().equals(folio.getCurrency())
                && credits.size()==1,"Deposit transfer identity mismatch");
        var payment=credits.get(0);
        require(payment.getId().equals(transfer.getPaymentId()) && "SUCCESS".equals(payment.getStatus())
                && payment.getAmount().compareTo(transfer.getAmount())==0
                && payment.getRequestKey().equals(transfer.getEventKey())
                && ("deposit-transfer:"+account.getId()).equals(transfer.getEventKey()),"Deposit transfer ledger mismatch");
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public DepositTransfer transferForCheckIn(Long stayId,Long actor) {
        var businessDate=businessDates.postingDate();
        access.employee(actor);
        var stay=stays.find(stayId);require(stay!=null && stay.getStatus()==1,"Deposit transfer requires an actual in-house Stay");
        var booking=bookings.selectByIdForUpdate(stay.getBookingId());require(booking!=null,"Reservation is missing");
        if("WALK_IN".equals(booking.getReservationSource()))return null;
        var identity=deposits.byBooking(booking.getId());require(identity!=null,"Reservation deposit account is missing");
        var account=deposits.lock(identity.getId());
        var receipts=deposits.payments(account.getId());var refunds=deposits.refunds(account.getId());var transfers=deposits.transfers(account.getId());
        var folio=folios.selectByStayIdForUpdate(stayId);
        require(folio!=null && folio.getClosedTime()==null && stayId.equals(folio.getStayId())
                && account.getCurrency().equals(folio.getCurrency()),"Deposit transfer Folio or currency mismatch");
        if(!transfers.isEmpty()) {
            var old=transfers.get(0);var payment=payments.selectById(old.getPaymentId());
            require(transfers.size()==1 && old.getStayId().equals(stayId) && old.getFolioId().equals(folio.getId())
                    && payment!=null && payment.getFolioId().equals(folio.getId()) && payment.getAmount().compareTo(old.getAmount())==0
                    && "DEPOSIT_TRANSFER".equals(payment.getPaymentMethod()) && "SUCCESS".equals(payment.getStatus())
                    && old.getEventKey().equals(payment.getRequestKey()),"Deposit transfer integrity violation");
            return old;
        }
        var amount=DepositLedgerRules.balance(receipts,refunds,transfers,deposits.settlements(account.getId())).available();
        require(amount.compareTo(booking.getTotalPrice())==0,
                "Check-in requires the full accepted-quote reservation deposit");
        if(amount.signum()==0)return null;
        String event="deposit-transfer:"+account.getId();
        var credit=Payment.builder().folioId(folio.getId()).amount(amount).paymentMethod("DEPOSIT_TRANSFER").status("SUCCESS")
                .requestKey(event).referenceNo("Reservation deposit account "+account.getId()).note("Prepayment credit; original receipt remains in Deposit Ledger")
                .createdBy(actor).paidTime(LocalDateTime.now(clock)).build();
        credit.setBusinessDate(businessDate);
        one(payments.insert(credit));
        var transfer=DepositTransfer.builder().accountId(account.getId()).stayId(stayId).folioId(folio.getId()).paymentId(credit.getId())
                .amount(amount).eventKey(event).transferredBy(actor).businessDate(businessDate).build();
        one(deposits.transfer(transfer));
        one(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(booking.getUserId()).action("DEPOSIT_TRANSFER")
                .detail("Reservation "+booking.getId()+", stay "+stayId+", deposit "+account.getId()+", folio "+folio.getId()+", transfer "+transfer.getId()).build()));
        return transfer;
    }
}
