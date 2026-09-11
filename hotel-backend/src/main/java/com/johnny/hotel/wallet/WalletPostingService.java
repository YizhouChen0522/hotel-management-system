package com.johnny.hotel.wallet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Internal money boundary. Debit requires a locked wallet and an amount-based, balance>=amount update.
 * Operation eligibility (including BLOCKED top-up restrictions) belongs to the initiating operation, not all credits/debits. */
@Service
@RequiredArgsConstructor
class WalletPostingService {
    private final WalletPostingMapper mapper;

    @Transactional(propagation=Propagation.MANDATORY)
    public void creditRefund(Wallet locked,Refund source,Long actor) {
        require(locked.getId().equals(source.getWalletId()) && locked.getUserId().equals(source.getUserId())
                && locked.getCurrency().equals(source.getCurrency()) && source.getStatus()==com.johnny.hotel.enums.RefundStatus.PENDING.getCode(),"Invalid refund source");
        var amount=money(source.getAmount(),12);require(amount.signum()>0,"Refund must be positive");
        append(locked,amount,WalletTransactionType.REFUND_CREDIT,"REFUND",source.getId(),actor,"refund:"+source.getId());
        one(mapper.credit(locked.getId(),amount));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void debitPayment(Wallet locked,com.johnny.hotel.entity.Payment source,Long actor) {
        var amount=money(source.getAmount(),12);require(amount.signum()>0 && locked.getBalance().compareTo(amount)>=0,"Insufficient wallet balance");
        require("WALLET".equals(source.getPaymentMethod()) && "SUCCESS".equals(source.getStatus()),"Invalid wallet payment source");
        append(locked,amount.negate(),WalletTransactionType.FOLIO_PAYMENT,"PAYMENT",source.getId(),actor,"checkout-wallet:"+source.getFolioId());
        one(mapper.debit(locked.getId(),amount));
    }
    private void append(Wallet wallet,java.math.BigDecimal amount,WalletTransactionType type,String source,Long id,Long actor,String key) {
        var after=money(wallet.getBalance().add(amount),12);require(after.signum()>=0,"Wallet cannot be negative");
        one(mapper.append(WalletTransaction.builder().walletId(wallet.getId()).transactionType(type.getCode()).amount(amount)
                .balanceBefore(wallet.getBalance()).balanceAfter(after).sourceType(source).sourceId(id).operatorUserId(actor).requestKey(key).build()));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void creditTopUp(Wallet locked,WalletTopUp request,Long actor,String key) {
        require(locked.getId().equals(request.getWalletId()),"Wrong wallet source");
        require(TopUpStatus.fromCode(request.getStatus())==TopUpStatus.PENDING,"Top-up is not pending");
        var amount=money(request.getAmount(),12);
        require(amount.signum()>0,"Top-up must be positive");
        var after=money(locked.getBalance().add(amount),12);
        one(mapper.append(WalletTransaction.builder().walletId(locked.getId()).transactionType(WalletTransactionType.TOP_UP.getCode())
                .amount(amount).balanceBefore(locked.getBalance()).balanceAfter(after).requestKey(key)
                .sourceType("TOP_UP_REQUEST").sourceId(request.getId()).operatorUserId(actor).build()));
        one(mapper.credit(locked.getId(),amount));
    }
}
