package com.johnny.hotel.booking.deposit;

import com.johnny.hotel.enums.RefundStatus;
import java.math.BigDecimal;
import java.util.List;
import static com.johnny.hotel.service.support.BillingRules.*;

/** All inputs must be current-read while the deposit account row is held. */
public final class DepositLedgerRules {
    private DepositLedgerRules() {}
    public record Balance(BigDecimal received, BigDecimal refunded, BigDecimal transferred,
                          BigDecimal pendingRefund, BigDecimal balance, BigDecimal available) {}
    public static Balance balance(List<DepositPayment> payments, List<DepositRefund> refunds,
                                  List<DepositTransfer> transfers) {
        BigDecimal received=BigDecimal.ZERO, refunded=BigDecimal.ZERO, pending=BigDecimal.ZERO, transferred=BigDecimal.ZERO;
        for(var p:payments) received=received.add(positive(p.getAmount()));
        for(var r:refunds) {
            var amount=positive(r.getAmount());
            switch(RefundStatus.fromCode(r.getStatus())) {
                case PENDING -> pending=pending.add(amount);
                case SUCCESS -> refunded=refunded.add(amount);
                case FAILED -> { }
            }
        }
        for(var t:transfers) transferred=transferred.add(positive(t.getAmount()));
        var balance=money(received.subtract(refunded).subtract(transferred),12);
        var available=money(balance.subtract(pending),12);
        require(balance.signum()>=0 && available.signum()>=0,"Deposit ledger is overdrawn or over-reserved");
        return new Balance(money(received,12),money(refunded,12),money(transferred,12),money(pending,12),balance,available);
    }
    public static BigDecimal positive(BigDecimal amount) {
        var value=money(amount,12);require(value.signum()>0,"Deposit amount must be positive");return value;
    }
    public static String key(String key) {
        require(key!=null && key.matches("[A-Za-z0-9_-]{8,64}"),"Invalid deposit request key");return key;
    }
}
