package com.johnny.hotel;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DepositLedgerRulesTest {
    private DepositPayment receipt(String amount) {return DepositPayment.builder().amount(new BigDecimal(amount)).build();}
    private DepositRefund refund(String amount,int status) {return DepositRefund.builder().amount(new BigDecimal(amount)).status(status).build();}
    @Test void pendingRefundReservesButDoesNotDischargeLiability() {
        var b=DepositLedgerRules.balance(List.of(receipt("100")),List.of(refund("30",0)),List.of());
        assertEquals(new BigDecimal("100.00"),b.balance());assertEquals(new BigDecimal("70.00"),b.available());
    }
    @Test void successfulRefundAndTransferDischargeLiabilityExactlyOnce() {
        var transfer=DepositTransfer.builder().amount(new BigDecimal("70")).build();
        var b=DepositLedgerRules.balance(List.of(receipt("100")),List.of(refund("30",1)),List.of(transfer));
        assertEquals(new BigDecimal("0.00"),b.available());assertEquals(new BigDecimal("0.00"),b.balance());
    }
    @Test void failedRefundDoesNotConsumeMoney() {
        var b=DepositLedgerRules.balance(List.of(receipt("100")),List.of(refund("100",2)),List.of());
        assertEquals(new BigDecimal("100.00"),b.available());
    }
    @Test void excessReservationsAreRejected() {
        assertThrows(BusinessException.class,()->DepositLedgerRules.balance(List.of(receipt("100")),List.of(refund("60",0),refund("60",0)),List.of()));
    }
    @Test void duplicateTransferCannotBeHiddenByNegativeBalance() {
        var t=DepositTransfer.builder().amount(new BigDecimal("100")).build();
        assertThrows(BusinessException.class,()->DepositLedgerRules.balance(List.of(receipt("100")),List.of(),List.of(t,t)));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","10000000000.00"})
    void invalidMoneyRejected(String value) {assertThrows(BusinessException.class,()->DepositLedgerRules.positive(new BigDecimal(value)));}
    @ParameterizedTest @ValueSource(strings={"short","space key","../../../../password"})
    void invalidRequestKeyRejected(String key) {assertThrows(BusinessException.class,()->DepositLedgerRules.key(key));}
}
