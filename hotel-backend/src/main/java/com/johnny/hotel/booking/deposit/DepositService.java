package com.johnny.hotel.booking.deposit;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.support.BillingAccess;
import com.johnny.hotel.stay.StayMapper;
import com.johnny.hotel.wallet.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Lock order: Booking -> deposit account -> receipts/refunds/transfers -> Wallet.
 * The check-in caller already owns Booking before creating Stay/Folio and transferring credit. */
@Service @RequiredArgsConstructor
public class DepositService {
    private final DepositMapper deposits;
    private final BookingMapper bookings;
    private final BookingPriceVersionMapper prices;
    private final StayMapper stays;
    private final WalletMapper wallets;
    private final WalletPostingService walletPosting;
    private final DepositRefundPosting posting;
    private final RefundAccess access;
    private final BillingAccess billing;
    private final SysAuditLogMapper audits;
    private final Clock clock;
    private final com.johnny.hotel.pagination.PaginationSupport pagination;

    private record Account(Booking booking,DepositAccount deposit) {}
    private Account lock(Long bookingId,Long actor) {
        var booking=bookings.selectByIdForUpdate(bookingId);
        boolean customer=access.isCustomer(actor);
        if(booking==null || customer && !actor.equals(booking.getUserId())) throw new BusinessException(404,"Deposit account not found");
        require(!"WALK_IN".equals(booking.getReservationSource()),"Walk-in reservations do not use the Reservation Deposit Ledger");
        if(booking.getUserId()==null)billing.operational(actor);else access.read(actor,booking.getUserId());
        var account=deposits.byBooking(bookingId);
        require(account!=null,"Reservation deposit account is missing");
        return new Account(booking,deposits.lock(account.getId()));
    }
    private DepositLedgerRules.Balance balance(DepositAccount account) {
        return DepositLedgerRules.balance(deposits.payments(account.getId()),deposits.refunds(account.getId()),deposits.transfers(account.getId()),deposits.settlements(account.getId()));
    }
    private String reason(String value) {
        require(value!=null && !value.isBlank() && value.length()<=255,"Reason is required (255 characters maximum)");return value.trim();
    }
    private void audit(Account a,Long actor,String action,Long source) {
        one(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(a.booking().getUserId()).action(action)
                .detail("Reservation "+a.booking().getId()+", deposit account "+a.deposit().getId()+", source "+source).build()));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public DepositAccount openForReservation(Long bookingId) {
        var booking=bookings.selectByIdForUpdate(bookingId);require(booking!=null,"Reservation does not exist");
        var old=deposits.byBooking(bookingId);if(old!=null)return old;
        require(stays.byBooking(bookingId)==null,"Cannot open a deposit account after check-in");
        var price=prices.selectActiveByBookingId(bookingId);require(price!=null,"Reservation price snapshot is missing");
        var account=DepositAccount.builder().bookingId(bookingId).currency(price.getCurrency()).build();
        one(deposits.open(account));return account;
    }
    @Transactional
    public DepositLedgerRules.Balance summary(Long bookingId) {
        var account=lock(bookingId,access.actor());return balance(account.deposit());
    }
    @Transactional
    public com.johnny.hotel.pagination.PageResult<DepositPayment> payments(Long bookingId,Integer page,Integer size) {
        var a=lock(bookingId,access.actor());var w=pagination.window(page,size,true);
        return pagination.result(w,deposits.paymentPage(a.deposit().getId(),w.offset(),pagination.limit(w)),deposits.paymentCount(a.deposit().getId()));
    }
    @Transactional
    public com.johnny.hotel.pagination.PageResult<DepositRefund> refunds(Long bookingId,Integer page,Integer size) {
        var a=lock(bookingId,access.actor());var w=pagination.window(page,size,true);
        return pagination.result(w,deposits.refundPage(a.deposit().getId(),w.offset(),pagination.limit(w)),deposits.refundCount(a.deposit().getId()));
    }
    @Transactional
    public com.johnny.hotel.pagination.PageResult<DepositSettlement> settlements(Long bookingId,Integer page,Integer size) {
        var a=lock(bookingId,access.actor());var w=pagination.window(page,size,true);
        return pagination.result(w,deposits.settlementPage(a.deposit().getId(),w.offset(),pagination.limit(w)),deposits.settlementCount(a.deposit().getId()));
    }
    @Transactional
    public DepositPayment receive(Long bookingId,DepositRequests.Receive request) {
        Long actor=billing.currentOperational();require(request!=null,"Deposit receipt is required");
        var amount=DepositLedgerRules.positive(request.getAmount());String key=DepositLedgerRules.key(request.getRequestKey());
        require(Set.of("CASH","CARD","BANK_TRANSFER").contains(request.getPaymentMethod()),"Unsupported deposit receipt method");
        String reference=request.getReferenceNo()==null || request.getReferenceNo().isBlank()?null:request.getReferenceNo().trim();
        require(reference==null || reference.length()<=100,"Payment reference is too long");
        var a=lock(bookingId,actor);
        var existing=deposits.payments(a.deposit().getId()).stream().filter(p->p.getRequestKey().equals(key)).findFirst().orElse(null);
        if(existing!=null) {
            require(existing.getAmount().compareTo(amount)==0 && existing.getPaymentMethod().equals(request.getPaymentMethod())
                    && Objects.equals(existing.getReferenceNo(),reference),"Deposit request key already used for another receipt");return existing;
        }
        require(stays.byBooking(bookingId)==null && Set.of(0,1).contains(a.booking().getStatus()),"New deposits require a pending or approved reservation before check-in");
        if(Set.of("CUSTOMER_PORTAL","STAFF_DIRECT").contains(a.booking().getReservationSource()))
            require(balance(a.deposit()).received().compareTo(a.booking().getTotalPrice())<0,
                    "Reservation already has its full accepted-quote deposit");
        money(balance(a.deposit()).received().add(amount),12);
        var receipt=DepositPayment.builder().accountId(a.deposit().getId()).amount(amount).paymentMethod(request.getPaymentMethod())
                .referenceNo(reference).requestKey(key).receivedBy(actor).receivedTime(LocalDateTime.now(clock)).build();
        one(deposits.receive(receipt));audit(a,actor,"DEPOSIT_RECEIVED",receipt.getId());return receipt;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public DepositPayment receivePortalWallet(Long bookingId,BigDecimal requiredAmount,String requestKey,Long customerId) {
        var amount=DepositLedgerRules.positive(requiredAmount);var key=DepositLedgerRules.key(requestKey);
        var booking=bookings.selectByIdForUpdate(bookingId);
        require(booking!=null && customerId.equals(booking.getUserId()) && "CUSTOMER_PORTAL".equals(booking.getReservationSource()),
                "Portal reservation does not belong to the current customer");
        var price=prices.selectActiveByBookingId(bookingId);
        require(price!=null && amount.compareTo(price.getTotalPrice())==0 && amount.compareTo(booking.getTotalPrice())==0,
                "Full accepted quote deposit is required");
        var account=deposits.byBooking(bookingId);require(account!=null,"Reservation deposit account is missing");
        var old=deposits.payments(account.getId()).stream().filter(p->key.equals(p.getRequestKey())).findFirst().orElse(null);
        if(old!=null){require(old.getAmount().compareTo(amount)==0&&"WALLET".equals(old.getPaymentMethod()),"Deposit request key already used");return old;}
        var walletIdentity=wallets.byUser(customerId);require(walletIdentity!=null&&account.getCurrency().equals(walletIdentity.getCurrency()),"Customer wallet or currency mismatch");
        var wallet=wallets.lock(walletIdentity.getId());
        require(wallet.getBalance().compareTo(amount)>=0,"Insufficient wallet balance for full reservation deposit");
        var receipt=DepositPayment.builder().accountId(account.getId()).amount(amount).paymentMethod("WALLET")
                .requestKey(key).receivedBy(customerId).receivedTime(LocalDateTime.now(clock)).build();
        one(deposits.receive(receipt));walletPosting.debitReservationDeposit(wallet,receipt,customerId);
        audit(new Account(booking,account),customerId,"PORTAL_FULL_DEPOSIT_RECEIVED",receipt.getId());
        return receipt;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void validatePortalFullDeposit(Long bookingId) {
        var booking=bookings.selectByIdForUpdate(bookingId);require(booking!=null,"Reservation does not exist");
        var account=deposits.byBooking(bookingId);require(account!=null,"Reservation deposit account is missing");
        var receipts=deposits.payments(account.getId());
        require(receipts.size()==1 && "WALLET".equals(receipts.get(0).getPaymentMethod())
                && receipts.get(0).getAmount().compareTo(booking.getTotalPrice())==0
                && booking.getPortalRequestKey().equals(receipts.get(0).getRequestKey()),
                "Portal reservation full-deposit integrity violation");
        var wallet=wallets.byUser(booking.getUserId());
        var ledger=wallet==null?null:wallets.bySource("DEPOSIT_PAYMENT",receipts.get(0).getId());
        require(ledger!=null && ledger.getWalletId().equals(wallet.getId())
                && ledger.getAmount().compareTo(booking.getTotalPrice().negate())==0,
                "Portal reservation wallet ledger integrity violation");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireFullGuarantee(Long bookingId) {
        var booking=bookings.selectByIdForUpdate(bookingId);require(booking!=null,"Reservation does not exist");
        if("WALK_IN".equals(booking.getReservationSource())) return;
        var account=deposits.byBooking(bookingId);require(account!=null,"Reservation deposit account is missing");
        require(balance(deposits.lock(account.getId())).available().compareTo(booking.getTotalPrice())==0,
                "Reservation approval requires the full accepted-quote deposit");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireUnfundedForContractReprice(Long bookingId) {
        var account=deposits.byBooking(bookingId);
        require(account!=null,"Reservation deposit account is missing");
        var locked=deposits.lock(account.getId());
        var current=balance(locked);
        require(current.received().signum()==0 && current.refunded().signum()==0
                        && current.transferred().signum()==0 && current.forfeited().signum()==0,
                "A funded reservation cannot be repriced; cancel and create a new reservation");
    }
    @Transactional
    public DepositRefund requestRefund(Long bookingId,RefundRequests.Create request) {
        Long actor=access.actor();require(request!=null,"Refund request is required");
        var amount=DepositLedgerRules.positive(request.getAmount());var key=DepositLedgerRules.key(request.getRequestKey());var reason=reason(request.getReason());
        var a=lock(bookingId,actor);access.create(actor,a.booking().getUserId());
        var existing=deposits.refunds(a.deposit().getId()).stream().filter(r->r.getRequestKey().equals(key)).findFirst().orElse(null);
        if(existing!=null) {require(existing.getAmount().compareTo(amount)==0 && existing.getReason().equals(reason),"Deposit refund key already used");return existing;}
        require(balance(a.deposit()).available().compareTo(amount)>=0,"Insufficient unreserved deposit credit");
        var wallet=wallets.byUser(a.booking().getUserId());
        require(wallet!=null && wallet.getCurrency().equals(a.deposit().getCurrency()),"Deposit refund wallet or currency mismatch");
        var refund=DepositRefund.builder().accountId(a.deposit().getId()).userId(a.booking().getUserId()).walletId(wallet.getId())
                .currency(a.deposit().getCurrency()).amount(amount).status(0).requestKey(key).reason(reason).requestedBy(actor).build();
        one(deposits.requestRefund(refund));audit(a,actor,"DEPOSIT_REFUND_REQUEST",refund.getId());return refund;
    }
    @Transactional
    public DepositRefund processRefund(Long bookingId,Long refundId,RefundRequests.Process request,boolean success) {
        Long actor=access.actor();access.operational(actor,true);require(request!=null,"Refund decision is required");
        String key=DepositLedgerRules.key(request.getRequestKey()),reason=reason(request.getReason());
        var a=lock(bookingId,actor);access.approve(actor,a.booking().getUserId());
        var refund=deposits.refunds(a.deposit().getId()).stream().filter(r->r.getId().equals(refundId)).findFirst().orElse(null);
        require(refund!=null,"Refund does not belong to this deposit account");
        require(!actor.equals(refund.getRequestedBy()),"Cannot approve your own refund request");
        int status=success?1:2;
        if(refund.getStatus()!=0) {
            require(refund.getStatus()==status && key.equals(refund.getProcessKey()) && reason.equals(refund.getProcessReason()),"Deposit refund already resolved with another decision");return refund;
        }
        balance(a.deposit());
        if(success) posting.credit(refund,actor);
        one(deposits.processRefund(refundId,status,actor,key,reason));
        audit(a,actor,success?"DEPOSIT_REFUND_SUCCESS":"DEPOSIT_REFUND_FAILED",refundId);
        return deposits.refunds(a.deposit().getId()).stream().filter(r->r.getId().equals(refundId)).findFirst().orElseThrow();
    }
}
