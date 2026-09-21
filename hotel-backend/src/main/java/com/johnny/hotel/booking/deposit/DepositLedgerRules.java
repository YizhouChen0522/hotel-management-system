package com.johnny.hotel.booking.deposit;

import com.johnny.hotel.enums.RefundStatus;
import java.math.BigDecimal;
import java.util.List;
import static com.johnny.hotel.service.support.BillingRules.*;

/** All inputs must be current-read while the deposit account row is held. */
public final class DepositLedgerRules {
    private DepositLedgerRules() {}
    public record Balance(BigDecimal received, BigDecimal refunded, BigDecimal transferred,
                          BigDecimal pendingRefund, BigDecimal balance, BigDecimal available,
                          BigDecimal forfeited, BigDecimal externalRefunded) {}
    public static Balance balance(List<DepositPayment> payments, List<DepositRefund> refunds,
                                  List<DepositTransfer> transfers) {
        return balance(payments,refunds,transfers,List.of());
    }
    public static Balance balance(List<DepositPayment> payments, List<DepositRefund> refunds,
                                  List<DepositTransfer> transfers,List<DepositSettlement> settlements) {
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
        BigDecimal forfeited=BigDecimal.ZERO, externalRefunded=BigDecimal.ZERO;
        for(var s:settlements){
            var amount=positive(s.getAmount());
            if("FORFEIT".equals(s.getKind()) && s.getStatus()==1)forfeited=forfeited.add(amount);
            else if("REFUND".equals(s.getKind()) && s.getStatus()==0)pending=pending.add(amount);
            else if("REFUND".equals(s.getKind()) && s.getStatus()==1)externalRefunded=externalRefunded.add(amount);
            else throw new com.johnny.hotel.exception.BusinessException("Invalid deposit settlement fact");
        }
        var balance=money(received.subtract(refunded).subtract(transferred).subtract(forfeited).subtract(externalRefunded),12);
        var available=money(balance.subtract(pending),12);
        require(balance.signum()>=0 && available.signum()>=0,"Deposit ledger is overdrawn or over-reserved");
        return new Balance(money(received,12),money(refunded,12),money(transferred,12),money(pending,12),balance,available,
                money(forfeited,12),money(externalRefunded,12));
    }
    public static BigDecimal positive(BigDecimal amount) {
        var value=money(amount,12);require(value.signum()>0,"Deposit amount must be positive");return value;
    }
    public static String key(String key) {
        require(key!=null && key.matches("[A-Za-z0-9_-]{8,64}"),"Invalid deposit request key");return key;
    }
}
