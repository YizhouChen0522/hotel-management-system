package com.johnny.hotel.wallet;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

class DepositDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired DepositService deposits;
    @Autowired DepositMapper depositMapper;
    long reservation() {
        long booking=createBooking("CUSTOMER");
        new TransactionTemplate(txManager).executeWithoutResult(status->deposits.openForReservation(booking));
        return booking;
    }
    DepositRequests.Receive receipt(String key) {return DepositRequests.Receive.builder().amount(new BigDecimal("100.00")).paymentMethod("CASH").requestKey(key).build();}
    RefundRequests.Create request(String key,String amount) {return RefundRequests.Create.builder().amount(new BigDecimal(amount)).requestKey(key).reason("Reservation cancelled").build();}
    RefundRequests.Process decision() {return RefundRequests.Process.builder().requestKey("confirm_001").reason("Verified receipt and beneficiary").build();}
    @AfterEach void removeOnlyDepositFixture() {
        gate.clear();
        for(long user:created) jdbc.update("DELETE FROM wallet_transaction WHERE wallet_id IN (SELECT id FROM wallet WHERE user_id=?) AND source_type='DEPOSIT_REFUND'",user);
        for(long b:bookingIds) {
            jdbc.update("DELETE FROM deposit_refund WHERE account_id IN (SELECT id FROM reservation_deposit_account WHERE booking_id=?)",b);
            jdbc.update("DELETE FROM deposit_payment WHERE account_id IN (SELECT id FROM reservation_deposit_account WHERE booking_id=?)",b);
            jdbc.update("DELETE FROM reservation_deposit_account WHERE booking_id=?",b);
        }
    }
    @Test void portalFullReceiptRemainsInReservationAndCannotBeOverpaid() {
        long b=reservation();var first=depositMapper.payments(depositMapper.byBooking(b).getId()).get(0);as("STAFF");
        assertThrows(BusinessException.class,()->deposits.receive(b,receipt("receipt_01")));
        assertEquals(new BigDecimal("300.00"),deposits.summary(b).balance());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        assertThrows(Exception.class,()->jdbc.update("UPDATE deposit_payment SET amount=99 WHERE id=?",first.getId()));
    }
    @Test void pendingRefundReservesCreditAndFailureReleasesIt() {
        long b=reservation();as("CUSTOMER");
        var r=deposits.requestRefund(b,request("refund_001","70"));
        assertEquals(new BigDecimal("230.00"),deposits.summary(b).available());
        assertThrows(BusinessException.class,()->deposits.requestRefund(b,request("refund_002","240")));
        as("MANAGER");deposits.processRefund(b,r.getId(),decision(),false);
        assertEquals(new BigDecimal("300.00"),deposits.summary(b).available());
        assertEquals(new BigDecimal("700.00"),wallets.find(wid("CUSTOMER")).getBalance());
    }
    @Test void depositRefundToBlockedWalletRemainsIdempotent() {
        long b=reservation();
        jdbc.update("UPDATE wallet SET status=0 WHERE id=?",wid("CUSTOMER"));
        as("CUSTOMER");var r=deposits.requestRefund(b,request("refund_001","100"));as("MANAGER");
        deposits.processRefund(b,r.getId(),decision(),true);deposits.processRefund(b,r.getId(),decision(),true);
        assertEquals(new BigDecimal("800.00"),wallets.find(wid("CUSTOMER")).getBalance());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transaction WHERE source_type='DEPOSIT_REFUND' AND source_id=?",Integer.class,r.getId()));
        assertEquals(new BigDecimal("200.00"),deposits.summary(b).balance());
    }
    @Test void customerOwnershipAndHrBoundary() throws Exception {
        long b=reservation();as("OTHER_CUSTOMER");assertThrows(BusinessException.class,()->deposits.summary(b));
        mvc.perform(get("/api/bookings/{id}/deposit",b).with(authentication(auth("HR_ADMIN")))).andExpect(status().isForbidden());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(get("/api/bookings/{id}/deposit",b).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
    }
    @Test void fullPortalDepositHasExactlyOneReceiptAndWalletDebit() {
        long b=reservation();var account=depositMapper.byBooking(b);
        assertEquals(1,depositMapper.payments(account.getId()).size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transaction WHERE source_type='DEPOSIT_PAYMENT' AND source_id=?",Integer.class,depositMapper.payments(account.getId()).get(0).getId()));
        as("MANAGER");assertEquals(new BigDecimal("300.00"),deposits.summary(b).balance());
    }
    @Test void refundFailureRollsBackWalletAndDecision() {
        long b=reservation();as("CUSTOMER");var r=deposits.requestRefund(b,request("refund_001","100"));
        as("MANAGER");gate.arm(Thread.currentThread().getName(),"DepositMapper.processRefund",true);
        assertThrows(Exception.class,()->deposits.processRefund(b,r.getId(),decision(),true));gate.clear();
        assertEquals(new BigDecimal("700.00"),wallets.find(wid("CUSTOMER")).getBalance());
        assertEquals(0,jdbc.queryForObject("SELECT status FROM deposit_refund WHERE id=?",Integer.class,r.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transaction WHERE source_type='DEPOSIT_REFUND' AND source_id=?",Integer.class,r.getId()));
    }
}
