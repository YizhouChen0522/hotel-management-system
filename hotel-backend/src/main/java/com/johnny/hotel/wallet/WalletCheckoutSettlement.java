package com.johnny.hotel.wallet;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class WalletCheckoutSettlement {
    private final WalletMapper wallets;
    private final WalletPostingService posting;
    private final PaymentMapper payments;
    private final RefundMapper refunds;
    private final SysAuditLogMapper audits;
    private final RefundAccess access;
    private final Clock clock;

    /** Caller holds Booking -> Room -> Folio -> ledger/Payment/Refund. No inverse acquisition from Wallet. */
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean contribute(Booking booking,Folio summary,Long actor) {
        access.operational(actor,false);access.customer(booking.getUserId());
        var identity=wallets.byUser(booking.getUserId());require(identity!=null,"Customer Wallet is missing");
        var wallet=wallets.lock(identity.getId());
        require(wallet.getCurrency().equals(summary.getCurrency()),"Wallet/Folio currency mismatch");
        String key="checkout-wallet:"+summary.getId();
        var existing=payments.selectByFolioIdAndRequestKey(summary.getId(),key);
        if(existing!=null) {
            validate(existing,wallet);return true;
        }
        var amount=wallet.getBalance().min(summary.getBalanceAmount());
        if(amount.signum()<=0)return false;
        var payment=Payment.builder().folioId(summary.getId()).amount(amount).paymentMethod("WALLET").status("SUCCESS")
                .requestKey(key).createdBy(actor).paidTime(LocalDateTime.now(clock)).note("Checkout wallet contribution").build();
        one(payments.insert(payment));require(payment.getId()!=null,"Payment id is missing");
        posting.debitPayment(wallet,payment,actor);
        one(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(booking.getUserId()).action("CHECKOUT_WALLET_PAYMENT")
                .detail("Folio "+summary.getId()+", payment "+payment.getId()+", amount "+amount).build()));
        return true;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void validateHistory(Booking booking,Long folioId) {
        var rows=payments.selectByFolioIdForUpdate(folioId).stream().filter(p->"WALLET".equals(p.getPaymentMethod())).toList();
        var credits=refunds.forFolio(folioId).stream().filter(r->r.getStatus()==com.johnny.hotel.enums.RefundStatus.SUCCESS.getCode()).toList();
        if(rows.isEmpty() && credits.isEmpty())return;
        var identity=wallets.byUser(booking.getUserId());require(identity!=null,"Wallet payment owner missing");var wallet=wallets.lock(identity.getId());
        for(var payment:rows)validate(payment,wallet);
        for(var refund:credits) {
            var ledger=wallets.bySource("REFUND",refund.getId());
            require(refund.getUserId().equals(booking.getUserId()) && refund.getWalletId().equals(wallet.getId())
                    && refund.getCurrency().equals(wallet.getCurrency()) && "HOTEL_WALLET".equals(refund.getDestination())
                    && ledger!=null && ledger.getWalletId().equals(wallet.getId())
                    && ledger.getTransactionType()==WalletTransactionType.REFUND_CREDIT.getCode()
                    && ledger.getAmount().compareTo(refund.getAmount())==0
                    && ledger.getOperatorUserId().equals(refund.getProcessedBy())
                    && ("refund:"+refund.getId()).equals(ledger.getRequestKey()),"Refund Wallet ledger integrity violation");
        }
    }
    private void validate(Payment payment,Wallet wallet) {
        var ledger=wallets.bySource("PAYMENT",payment.getId());
        require("SUCCESS".equals(payment.getStatus()) && "WALLET".equals(payment.getPaymentMethod())
                && ("checkout-wallet:"+payment.getFolioId()).equals(payment.getRequestKey()) && ledger!=null
                && ledger.getWalletId().equals(wallet.getId()) && ledger.getTransactionType()==WalletTransactionType.FOLIO_PAYMENT.getCode()
                && ledger.getAmount().compareTo(payment.getAmount().negate())==0,"Wallet Payment ledger integrity violation");
    }
}
