package com.johnny.hotel.wallet;

import com.johnny.hotel.dto.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.support.*;
import com.johnny.hotel.enums.RefundStatus;
import com.johnny.hotel.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class RefundSettlementDevelopmentTest extends FinancialDevelopmentFixture {
    @Test void migrationAndRefundReservationLifecycle(){
        assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='11'",Integer.class));long b=stay();pay(b,"400");var r=requestRefund(b,"60","refund_01");assertEquals(RefundStatus.PENDING,r.status());
        assertEquals(r.id(),requestRefund(b,"60.00","refund_01").id());assertThrows(BusinessException.class,()->requestRefund(b,"61","refund_01"));assertThrows(BusinessException.class,()->requestRefund(b,"41","refund_02"));
        var next=requestRefund(b,"40","refund_02");as("MANAGER");refunds.confirm(folio(b),r.id(),process("process_01"));
        assertEquals(new BigDecimal("60.00"),wallets.find(wid("CUSTOMER")).getBalance());assertEquals(new BigDecimal("-40.00"),queries.byBooking(b,uid("CUSTOMER")).balanceAmount());
        refunds.confirm(folio(b),next.id(),process("process_02"));assertEquals(0,queries.byBooking(b,uid("CUSTOMER")).balanceAmount().signum());assertEquals(new BigDecimal("100.00"),queries.byBooking(b,uid("CUSTOMER")).refundedAmount());invariantBooking(b);
    }
    @ParameterizedTest @ValueSource(strings={"0","299","300"}) void noCreditCannotRefund(String paid){long b=stay();if(!paid.equals("0"))pay(b,paid);assertThrows(BusinessException.class,()->requestRefund(b,"1","refund_01"));invariantBooking(b);}
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","10000000000","101"}) void invalidOrExcessRefundAmount(String amount){long b=stay();pay(b,"400");assertThrows(BusinessException.class,()->requestRefund(b,amount,"refund_01"));invariantBooking(b);}
    @Test void failedRefundReleasesReservationWithoutCredit(){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");as("OWNER");var result=refunds.fail(folio(b),r.id(),process("process_01"));assertEquals(RefundStatus.FAILED,result.status());assertEquals(result,refunds.fail(folio(b),r.id(),process("process_01")));assertThrows(BusinessException.class,()->refunds.confirm(folio(b),r.id(),process("process_02")));assertEquals(0,wallets.transactions(wid("CUSTOMER"),0).size());assertEquals(0,queries.byBooking(b,uid("CUSTOMER")).refundedAmount().signum());requestRefund(b,"100","refund_02");invariantBooking(b);}
    @Test void confirmRetryAndBlockedWalletCreditAndSummaryJson()throws Exception{
        long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");as("STAFF");service.status(wid("CUSTOMER"),state(WalletStatus.BLOCKED));as("MANAGER");var result=refunds.confirm(folio(b),r.id(),process("process_01"));assertEquals(result,refunds.confirm(folio(b),r.id(),process("process_01")));
        var ledger=wallets.transactions(wid("CUSTOMER"),0);assertEquals(1,ledger.size());assertEquals(WalletTransactionType.REFUND_CREDIT.getCode(),ledger.get(0).getTransactionType());assertEquals(r.id(),ledger.get(0).getSourceId());assertEquals("REFUND",ledger.get(0).getSourceType());
        mvc.perform(get("/api/bookings/"+b+"/folio").with(authentication(auth("CUSTOMER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.refundedAmount").value(100)).andExpect(jsonPath("$.data.balanceAmount").value(0));
        mvc.perform(get("/api/folios/"+folio(b)+"/refunds").with(authentication(auth("CUSTOMER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
        assertThrows(Exception.class,()->jdbc.update("UPDATE refund SET status=0 WHERE id=?",r.id()));assertThrows(Exception.class,()->jdbc.update("UPDATE refund SET amount=1 WHERE id=?",r.id()));clock.day(3);checkout(b);invariantBooking(b);
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN"}) void refundHttpPermissions(String role)throws Exception{
        long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");String path="/api/folios/"+folio(b)+"/refunds";
        mvc.perform(get(path).with(authentication(auth(role)))).andExpect(status().is(role.equals("HR_ADMIN")?403:200));
        mvc.perform(post(path+"/"+r.id()+"/confirm").with(authentication(auth(role))).contentType("application/json").content("{\"requestKey\":\"process_01\",\"reason\":\"reviewed\"}"))
                .andExpect(status().is(Set.of("MANAGER","OWNER","SUPER_ADMIN").contains(role)?200:403));invariantBooking(b);
    }
    @Test void idorAndCrossFolioRefundReferencesAreDenied()throws Exception{long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");long other=createBooking("OTHER_CUSTOMER");
        mvc.perform(get("/api/folios/"+folio(b)+"/refunds").with(authentication(auth("OTHER_CUSTOMER")))).andExpect(status().isNotFound());as("OTHER_CUSTOMER");assertThrows(Exception.class,()->refunds.create(folio(b),refundRequest("1","refund_02")));as("MANAGER");assertThrows(Exception.class,()->refunds.confirm(folio(other),r.id(),process("process_01")));invariantBooking(b);}
    @Test void confirmationRechecksCreditAfterAdditionalCharges(){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");as("MANAGER");var fee=expenses.register(folio(b),expense("10"));expenses.confirm(folio(b),fee.id());assertThrows(BusinessException.class,()->refunds.confirm(folio(b),r.id(),process("process_01")));assertEquals(0,wallets.find(wid("CUSTOMER")).getBalance().signum());clock.day(3);assertThrows(BusinessException.class,()->checkout(b));invariantBooking(b);}
    @ParameterizedTest @ValueSource(strings={"WalletPostingMapper.append","WalletPostingMapper.credit","RefundMapper.process","SysAuditLogMapper.insert","FolioMapper.updateFinancialSummary"})
    void refundFailureIsAtomic(String statement){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");as("MANAGER");gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->refunds.confirm(folio(b),r.id(),process("process_01")));assertEquals(RefundStatus.PENDING,refunds.list(folio(b)).get(0).status());assertEquals(0,wallets.find(wid("CUSTOMER")).getBalance().signum());assertEquals(0,wallets.transactions(wid("CUSTOMER"),0).size());invariantBooking(b);}
    @ParameterizedTest @ValueSource(booleans={true,false}) void fullWalletCheckoutAndBlockedSemantics(boolean blocked){long b=stay();creditWallet("350");if(blocked){as("STAFF");service.status(wid("CUSTOMER"),state(WalletStatus.BLOCKED));}clock.day(3);checkout(b);assertEquals(3,bookingState(b));assertEquals(new BigDecimal("50.00"),wallets.find(wid("CUSTOMER")).getBalance());var view=queries.byBooking(b,uid("CUSTOMER"));assertEquals(1,view.payments().size());assertEquals("WALLET",view.payments().get(0).getPaymentMethod());var rows=wallets.transactions(wid("CUSTOMER"),0);assertEquals(2,rows.size());assertEquals(new BigDecimal("-300.00"),rows.get(1).getAmount());assertEquals(3,rows.get(1).getTransactionType());assertThrows(BusinessException.class,()->checkout(b));invariantBooking(b);}
    @Test void partialContributionCommitsOnceAndOrdinaryPaymentFinishesCheckout()throws Exception{
        long b=stay();creditWallet("100");clock.day(3);assertThrows(WalletSettlementIncompleteException.class,()->checkout(b));assertEquals(2,bookingState(b));assertEquals(new BigDecimal("200.00"),queries.byBooking(b,uid("CUSTOMER")).balanceAmount());assertEquals(0,wallets.find(wid("CUSTOMER")).getBalance().signum());
        creditWallet("50");mvc.perform(post("/api/admin/bookings/"+b+"/check-out").with(authentication(auth("MANAGER")))).andExpect(status().isConflict());assertEquals(new BigDecimal("50.00"),wallets.find(wid("CUSTOMER")).getBalance());assertEquals(1,queries.byBooking(b,uid("CUSTOMER")).payments().size());pay(b,"200");checkout(b);invariantBooking(b);
    }
    @Test void zeroWalletLeavesOutstandingDebtAndNegativeBalanceRequiresExplicitRefund(){long b=stay();clock.day(3);assertThrows(BusinessException.class,()->checkout(b));assertEquals(0,queries.byBooking(b,uid("CUSTOMER")).payments().size());pay(b,"400");assertThrows(BusinessException.class,()->checkout(b));assertEquals(0,refundsCount(b));invariantBooking(b);}
    int refundsCount(long b){return jdbc.queryForObject("SELECT COUNT(*) FROM refund WHERE folio_id=?",Integer.class,folio(b));}
    @ParameterizedTest @ValueSource(strings={"PaymentMapper.insert","WalletPostingMapper.append","WalletPostingMapper.debit","SysAuditLogMapper.insert","FolioMapper.close","BookingMapper.transitionStatus","RoomMapper.transitionStatus"})
    void checkoutFinancialAndLifecycleFailuresRollbackTogether(String statement){long b=stay();creditWallet("300");clock.day(3);gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->checkout(b));assertEquals(2,bookingState(b));assertEquals(new BigDecimal("300.00"),wallets.find(wid("CUSTOMER")).getBalance());assertEquals(0,queries.byBooking(b,uid("CUSTOMER")).payments().size());assertNull(queries.byBooking(b,uid("CUSTOMER")).closedTime());invariantBooking(b);}
    @Test void finalizedFolioNeverReopensAndNewRefundIsRejected(){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");as("MANAGER");refunds.confirm(folio(b),r.id(),process("process_01"));clock.day(3);checkout(b);var closed=queries.byBooking(b,uid("CUSTOMER")).closedTime();assertEquals(RefundStatus.SUCCESS,refunds.confirm(folio(b),r.id(),process("process_01")).status());assertThrows(BusinessException.class,()->requestRefund(b,"1","refund_02"));assertEquals(closed,queries.byBooking(b,uid("CUSTOMER")).closedTime());invariantBooking(b);}


    @Test void requestAndFailedDecisionAuditFailuresRollback(){
        long b=stay();pay(b,"400");gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);
        assertThrows(Exception.class,()->requestRefund(b,"100","refund_01"));assertEquals(0,refundsCount(b));
        var r=requestRefund(b,"100","refund_01");as("MANAGER");gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);
        assertThrows(Exception.class,()->refunds.fail(folio(b),r.id(),process("process_01")));assertEquals(RefundStatus.PENDING,refunds.list(folio(b)).get(0).status());invariantBooking(b);
    }
    @Test void reusedProcessingKeyRollsBackSecondCredit(){
        long b=stay();pay(b,"400");var a=requestRefund(b,"50","refund_01");var c=requestRefund(b,"50","refund_02");as("MANAGER");
        refunds.confirm(folio(b),a.id(),process("process_01"));assertThrows(Exception.class,()->refunds.confirm(folio(b),c.id(),process("process_01")));
        assertEquals(new BigDecimal("50.00"),wallets.find(wid("CUSTOMER")).getBalance());assertEquals(1,wallets.transactions(wid("CUSTOMER"),0).size());invariantBooking(b);
    }
    @Test void ordinaryPaymentCannotForgeWalletSource(){long b=stay();var r=payRequest("100");r.setPaymentMethod("WALLET");assertThrows(BusinessException.class,()->payments.recordPayment(folio(b),r,uid("STAFF")));assertEquals(0,queries.byBooking(b,uid("CUSTOMER")).payments().size());invariantBooking(b);}
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN"})
    void selfReviewAndEmployeeRefundAreAlwaysDenied(String role){
        long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");
        if(!role.equals("CUSTOMER"))jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",uid("CUSTOMER"),role);
        as("CUSTOMER");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->refunds.confirm(folio(b),r.id(),process("process_01")));
        assertEquals(0,wallets.find(wid("CUSTOMER")).getBalance().signum());
    }
    @ParameterizedTest @ValueSource(strings={"PAYMENT","REFUND"})
    void missingWalletSourceLedgerBlocksCheckout(String source){
        long b=stay();
        if(source.equals("REFUND")){pay(b,"400");var r=requestRefund(b,"100","refund_01");as("MANAGER");refunds.confirm(folio(b),r.id(),process("process_01"));}
        else{creditWallet("100");clock.day(3);assertThrows(WalletSettlementIncompleteException.class,()->checkout(b));pay(b,"200");}
        jdbc.update("DELETE FROM wallet_transaction WHERE wallet_id=? AND source_type=?",wid("CUSTOMER"),source);
        clock.day(3);assertThrows(BusinessException.class,()->checkout(b));assertNull(queries.byBooking(b,uid("CUSTOMER")).closedTime());
    }




    @Test void concurrentRequestsCannotOverReserve(){long b=stay();pay(b,"400");assertInstanceOf(BusinessException.class,serialized("RefundMapper.insert",()->requestRefund(b,"100","refund_01"),()->requestRefund(b,"100","refund_02")));assertEquals(1,refundsCount(b));invariantBooking(b);}
    @Test void concurrentConfirmCannotDoubleCredit(){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");assertNull(serialized("RefundMapper.process",()->refunds.confirm(folio(b),r.id(),process("process_01")),()->refunds.confirm(folio(b),r.id(),process("process_01"))));assertEquals(1,wallets.transactions(wid("CUSTOMER"),0).size());invariantBooking(b);}
    @Test void concurrentCheckoutsCannotDoubleDebit(){long b=stay();creditWallet("300");clock.day(3);assertInstanceOf(BusinessException.class,serialized("PaymentMapper.insert",()->checkout(b),()->checkout(b)));assertEquals(1,queries.byBooking(b,uid("CUSTOMER")).payments().size());invariantBooking(b);}
    @Test void differentFoliosSharingWalletCannotOverdraw(){long a=stay();long b=createBooking("CUSTOMER");var r=new ApproveBookingRequest();r.setAssignedRoomId(room2);bookings.approveBooking(b,r,uid("MANAGER"));bookings.checkIn(b,uid("MANAGER"));creditWallet("300");clock.day(3);
        assertInstanceOf(BusinessException.class,serialized("WalletPostingMapper.debit",()->checkout(a),()->checkout(b)));assertEquals(0,wallets.find(wid("CUSTOMER")).getBalance().signum());invariantBooking(a);invariantBooking(b);}
    @ParameterizedTest @ValueSource(booleans={true,false}) void ordinaryPaymentAndWalletCheckoutBothOrders(boolean paymentFirst){long b=stay();creditWallet("300");clock.day(3);
        if(paymentFirst)assertNull(serialized("PaymentMapper.insert",()->pay(b,"100"),()->checkout(b)));
        else assertInstanceOf(BusinessException.class,serialized("FolioMapper.close",()->checkout(b),()->pay(b,"100")));
        invariantBooking(b);assertEquals(paymentFirst?new BigDecimal("100.00"):new BigDecimal("0.00"),wallets.find(wid("CUSTOMER")).getBalance());}
    @ParameterizedTest @ValueSource(booleans={true,false}) void refundAndPaymentBothOrders(boolean refundFirst){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");
        if(refundFirst)assertNull(serialized("RefundMapper.process",()->refunds.confirm(folio(b),r.id(),process("process_01")),()->pay(b,"10")));
        else assertNull(serialized("PaymentMapper.insert",()->pay(b,"10"),()->refunds.confirm(folio(b),r.id(),process("process_01"))));
        assertEquals(new BigDecimal("-10.00"),queries.byBooking(b,uid("CUSTOMER")).balanceAmount());invariantBooking(b);}
    @Test void refundConfirmationAllowsWaitingCheckout(){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");clock.day(3);assertNull(serialized("RefundMapper.process",()->refunds.confirm(folio(b),r.id(),process("process_01")),()->checkout(b)));invariantBooking(b);}

    // Ported existing lifecycle/concurrency regression scenarios; only fixture IDs and cleanup are scoped to this run.
    @ParameterizedTest @ValueSource(booleans={true,false}) void existingPaymentSameAndDifferentKeyConcurrency(boolean same){long b=createBooking("CUSTOMER");var a=payRequest("10");var c=same?a:payRequest("15");assertNull(serialized("FolioMapper.selectByIdForUpdate",()->payments.recordPayment(folio(b),a,uid("STAFF")),()->payments.recordPayment(folio(b),c,uid("STAFF"))));assertEquals(same?1:2,queries.byBooking(b,uid("CUSTOMER")).payments().size());invariantBooking(b);}
    @Test void existingPaymentWinsCheckoutSeesCredit(){long b=stay();pay(b,"300");clock.day(3);assertInstanceOf(BusinessException.class,serialized("PaymentMapper.insert",()->pay(b,"1"),()->checkout(b)));assertEquals(2,bookingState(b));invariantBooking(b);}
    @ParameterizedTest @ValueSource(booleans={true,false}) void existingExpensePaymentConcurrency(boolean expenseFirst){long b=stay();pay(b,"300");as("MANAGER");var fee=expenses.register(folio(b),expense("10"));
        if(expenseFirst)assertNull(serialized("ExpenseMapper.resolve",()->expenses.confirm(folio(b),fee.id()),()->pay(b,"10")));
        else assertNull(serialized("PaymentMapper.insert",()->pay(b,"10"),()->expenses.confirm(folio(b),fee.id())));
        clock.day(3);checkout(b);invariantBooking(b);}
    @Test void existingPendingExpenseBlocksCheckoutBeforeWalletDebit(){long b=stay();creditWallet("400");as("MANAGER");expenses.register(folio(b),expense("10"));clock.day(3);assertThrows(BusinessException.class,()->checkout(b));assertEquals(new BigDecimal("400.00"),wallets.find(wid("CUSTOMER")).getBalance());invariantBooking(b);}
    @Test void existingMissingNightOrRoomChargeCannotBeHiddenByWallet(){long b=stay();creditWallet("300");jdbc.update("DELETE FROM folio_item WHERE folio_id=? ORDER BY id LIMIT 1",folio(b));financial.recalculateSummary(b);clock.day(3);assertThrows(BusinessException.class,()->checkout(b));assertEquals(new BigDecimal("300.00"),wallets.find(wid("CUSTOMER")).getBalance());invariantBooking(b);}
    @ParameterizedTest @ValueSource(strings={"room","assignment","event","reversal"}) void existingCheckoutCorruptHistoryStillBlocks(String kind){long b=stay();clock.day(1);var change=new ChangeRoomDuringStayRequest();change.setNewRoomId(room3);change.setReason("test move");bookings.changeRoomDuringStay(b,change,uid("MANAGER"));creditWallet("500");clock.day(3);
        switch(kind){case "room"->jdbc.update("UPDATE room SET status=1 WHERE id=?",room3);case "assignment"->jdbc.update("UPDATE booking SET assigned_room_id=? WHERE id=?",room4,b);case "event"->jdbc.update("DELETE FROM room_billing_event WHERE booking_id=?",b);default->jdbc.update("DELETE FROM folio_item WHERE folio_id=? AND item_type='ROOM_RATE_ADJUSTMENT' ORDER BY id LIMIT 1",folio(b));}
        assertThrows(BusinessException.class,()->checkout(b));assertEquals(new BigDecimal("500.00"),wallets.find(wid("CUSTOMER")).getBalance());assertNull(queries.byBooking(b,uid("CUSTOMER")).closedTime());}
    @ParameterizedTest @ValueSource(ints={0,2,4}) void existingUnsupportedCheckoutDateRemainsBlocked(int day){long b=stay();creditWallet("300");clock.day(day);assertThrows(BusinessException.class,()->checkout(b));assertEquals(new BigDecimal("300.00"),wallets.find(wid("CUSTOMER")).getBalance());invariantBooking(b);}
    @Test void existingOuterSnapshotCannotLosePaymentOrRefund(){long b=stay();pay(b,"400");var r=requestRefund(b,"100","refund_01");var pool=Executors.newSingleThreadExecutor();
        try{new TransactionTemplate(txManager).executeWithoutResult(s->{assertEquals(0,jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM refund WHERE folio_id=? AND status=1",BigDecimal.class,folio(b)).signum());assertNull(result(worker(pool,"refund-writer",()->refunds.confirm(folio(b),r.id(),process("process_01")))));financial.recalculateSummary(b);});assertEquals(new BigDecimal("100.00"),queries.byBooking(b,uid("CUSTOMER")).refundedAmount());invariantBooking(b);}
        finally{pool.shutdownNow();}}
}

