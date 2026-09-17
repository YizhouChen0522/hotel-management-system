package com.johnny.hotel.wallet;
import com.johnny.hotel.support.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MysqlConcurrencyTest extends FinancialDevelopmentFixture {
    @org.springframework.beans.factory.annotation.Autowired com.johnny.hotel.booking.deposit.DepositService deposits;
    long create(){return createBooking("CUSTOMER");}
    void approve(long b){var r=new ApproveBookingRequest();r.setAssignedRoomId(room1);bookings.approveBooking(b,r,uid("MANAGER"));register(b);}
    long checkIn(){return stay();}
    int state(long b){return bookingState(b);}
    void change(long b,long room){var r=new ChangeRoomDuringStayRequest();r.setNewRoomId(room);r.setReason("test move");bookings.changeRoomDuringStay(b,r,uid("MANAGER"));}
    FolioItemCommand fee(String key,String amount){return FolioItemCommand.builder().itemType("SERVICE_CHARGE").description("test service").businessDate(arrival).quantity(BigDecimal.ONE).unitPrice(new BigDecimal(amount)).amount(new BigDecimal(amount)).eventKey(key).build();}
    @Override void pay(long b,String amount){if(jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b)==0){as("STAFF");deposits.receive(b,com.johnny.hotel.booking.deposit.DepositRequests.Receive.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").requestKey(java.util.UUID.randomUUID().toString()).build());}else super.pay(b,amount);}
    void invariants(long b){if(jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b)==0){assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio f JOIN stay s ON s.id=f.stay_id WHERE s.booking_id=?",Integer.class,b));return;} if(!jdbc.queryForObject("SELECT user_id FROM booking WHERE id=?",Long.class,b).equals(uid("CUSTOMER")))return;invariantBooking(b);}


    private void stop(ExecutorService pool) {
        pool.shutdown();
        try { if (!pool.awaitTermination(20,TimeUnit.SECONDS)) {pool.shutdownNow();fail("Concurrent workers did not finish");} }
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    Future<Throwable> worker(ExecutorService pool,String name,Runnable action) {
        return pool.submit(()-> {Thread.currentThread().setName(name);as("MANAGER");try {action.run();return null;} catch(Throwable e){return e;} });
    }
    Throwable result(Future<Throwable> future) {
        try {return future.get(20,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}
    }
    private void businessFailure(Throwable failure) {
        assertNotNull(failure);Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
        assertInstanceOf(BusinessException.class,cause);
    }
    private void databaseWaitObserved() {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.innodb_lock_waits",Integer.class)>0)return;
            // MySQL 5.7 caches lock observation tables. Ordering depends on an observed wait.
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
        }
        fail("Expected real InnoDB lock wait was not observed");
    }
    Throwable serialized(String lockStatement,Runnable first,Runnable second) {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        gate.arm("first",lockStatement,false);
        try {
            Future<Throwable> a=worker(pool,"first",first);SqlGate.await(gate.reached);
            Future<Throwable> b=worker(pool,"second",second);databaseWaitObserved();gate.release.countDown();
            assertNull(result(a));return result(b);
        } finally {gate.clear();stop(pool);}
    }
    @Test void approveWinsRejectCannotOverwrite() {
        long b=create();businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->approve(b),()->bookings.rejectBooking(b,uid("MANAGER"))));
        assertEquals(1,state(b));invariants(b);
    }
    @Test void rejectWinsApprovalCannotReserveRoom() {
        long b=create();businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->bookings.rejectBooking(b,uid("MANAGER")),()->approve(b)));
        assertEquals(5,state(b));assertEquals(1,jdbc.queryForObject("SELECT status FROM room WHERE id="+room1,Integer.class));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"cancel","admin-cancel","repeat-checkin","checkout"}) void checkInSerializesLifecycle(String contender) {
        long b=create();approve(b);
        if(contender.equals("checkout")) {
            var pool=Executors.newFixedThreadPool(2);gate.arm("arriving","BookingMapper.selectByIdForUpdate",false);
            try {
                var arrival=worker(pool,"arriving",()->bookings.checkIn(b,uid("MANAGER")));SqlGate.await(gate.reached);
                businessFailure(result(worker(pool,"departing",()->bookings.checkOut(b,uid("MANAGER")))));
                gate.release.countDown();assertNull(result(arrival));
            } finally {gate.clear();stop(pool);}
            assertEquals(1,stayState(b));invariants(b);return;
        }
        businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->bookings.checkIn(b,uid("MANAGER")),()-> {
            switch(contender){case "cancel"->bookings.cancelBooking(b,uid("CUSTOMER"));case "admin-cancel"->bookings.cancelBookingByAdmin(b,uid("MANAGER"));case "repeat-checkin"->bookings.checkIn(b,uid("MANAGER"));default->bookings.checkOut(b,uid("MANAGER"));}
        }));assertEquals(1,stayState(b));invariants(b);
    }
    @Test void cancellationWinsCheckInCannotUseReleasedRoom() {
        long b=create();approve(b);businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->bookings.cancelBookingByAdmin(b,uid("MANAGER")),()->bookings.checkIn(b,uid("MANAGER"))));
        assertEquals(4,state(b));invariants(b);
    }
    @Test void roomMaintenanceWinsApprovalRejects() {
        long b=create();businessFailure(serialized("RoomMapper.selectByIdForUpdate",()->rooms.setRoomMaintenance(room1),()->approve(b)));
        assertEquals(0,state(b));assertEquals(3,jdbc.queryForObject("SELECT status FROM room WHERE id="+room1,Integer.class));invariants(b);
    }
    @Test void approvalWinsMaintenanceCannotReleaseReservation() {
        long b=create();businessFailure(serialized("RoomMapper.selectByIdForUpdate",()->approve(b),()->rooms.setRoomMaintenance(room1)));
        assertEquals(1,state(b));invariants(b);
    }
    @ParameterizedTest @ValueSource(booleans={true,false}) void concurrentPaymentsSameAndDifferentKeys(boolean sameKey) {
        long b=checkIn();long f=folio(b);var a=payRequest("10");var c=sameKey?a:payRequest("15");
        assertNull(serialized("FolioMapper.selectByIdForUpdate",()->payments.recordPayment(f,a,uid("MANAGER")),()->payments.recordPayment(f,c,uid("MANAGER"))));
        assertEquals(sameKey?1:2,queries.byBooking(b,uid("CUSTOMER")).payments().size());assertEquals(sameKey?10:25,queries.byBooking(b,uid("CUSTOMER")).paidAmount().intValueExact());invariants(b);
    }
    @Test void concurrentSameKeyDifferentContentRejectsSecond() {
        long b=checkIn();long f=folio(b);var a=payRequest("10");var c=payRequest("11");c.setIdempotencyKey(a.getIdempotencyKey());
        businessFailure(serialized("FolioMapper.selectByIdForUpdate",()->payments.recordPayment(f,a,uid("MANAGER")),()->payments.recordPayment(f,c,uid("MANAGER"))));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"checkin","change","add","summary"}) void outerRepeatableReadSnapshotCannotOverwriteCommittedPayment(String operation) {
        long b=operation.equals("checkin")?create():checkIn();if(operation.equals("checkin"))approve(b);
        if(operation.equals("change"))clock.day(1);
        CountDownLatch snapshot=new CountDownLatch(1),committed=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first=worker(pool,"rr-reader",()-> {
                var tx=new TransactionTemplate(txManager);tx.setIsolationLevel(4);
                tx.executeWithoutResult(status -> {
                    assertEquals("REPEATABLE-READ",jdbc.queryForObject("SELECT @@tx_isolation",String.class));
                    assertEquals(0,jdbc.queryForObject(operation.equals("checkin")?"SELECT COALESCE(SUM(amount),0) FROM deposit_payment WHERE account_id=(SELECT id FROM reservation_deposit_account WHERE booking_id=?)":"SELECT COALESCE(SUM(amount),0) FROM payment WHERE folio_id=?",BigDecimal.class,operation.equals("checkin")?b:folio(b)).signum());
                    snapshot.countDown();SqlGate.await(committed);
                    // Control reproduces BILL-006: ordinary SUM remains stale inside this outer transaction.
                    assertEquals(0,jdbc.queryForObject(operation.equals("checkin")?"SELECT COALESCE(SUM(amount),0) FROM deposit_payment WHERE account_id=(SELECT id FROM reservation_deposit_account WHERE booking_id=?)":"SELECT COALESCE(SUM(amount),0) FROM payment WHERE folio_id=?",BigDecimal.class,operation.equals("checkin")?b:folio(b)).signum());
                    switch(operation){case "checkin"->bookings.checkIn(b,uid("MANAGER"));case "change"->change(b,room3);case "add"->folios.addItem(sid(b),fee("concurrent","20"),uid("MANAGER"));default->financial.recalculateSummary(folio(b));}
                });
            });
            SqlGate.await(snapshot);Future<Throwable> second=worker(pool,"payer",()->pay(b,"10"));assertNull(result(second));committed.countDown();assertNull(result(first));
        } finally {committed.countDown();stop(pool);}
        assertEquals(10,queries.byBooking(b,uid("CUSTOMER")).paidAmount().intValueExact());invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"change","add"}) void bookingWriterPausedBeforeFolioDoesNotDeadlockPayment(String operation) {
        long b=operation.equals("checkin")?create():checkIn();if(operation.equals("checkin"))approve(b);
        var pool=Executors.newFixedThreadPool(2);gate.arm("booking-writer","BookingMapper.selectByIdForUpdate",false);
        try {
            var a=worker(pool,"booking-writer",()->{switch(operation){case "checkin"->bookings.checkIn(b,uid("MANAGER"));case "change"->change(b,room3);default->folios.addItem(sid(b),fee("concurrent","10"),uid("MANAGER"));}});
            SqlGate.await(gate.reached);var p=worker(pool,"payer",()->pay(b,"5"));assertNull(result(p));gate.release.countDown();assertNull(result(a));
        } finally {gate.clear();stop(pool);}invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"payment","add","change"}) void checkoutWinsFinalizationBlocksContenders(String operation) {
        long b=checkIn();pay(b,"300");clock.day(3);
        businessFailure(serialized("FolioMapper.selectByStayIdForUpdate",()->bookings.checkOut(b,uid("MANAGER")),()-> {
            switch(operation){case "payment"->pay(b,"1");case "add"->folios.addItem(sid(b),fee("late","1"),uid("MANAGER"));default->change(b,room3);}
        }));assertEquals(2,stayState(b));invariants(b);
    }
    @Test void paymentWinsCheckoutSeesCreditAndCannotFinalize() {
        long b=checkIn();pay(b,"300");clock.day(3);
        businessFailure(serialized("FolioMapper.selectByIdForUpdate",()->pay(b,"1"),()->bookings.checkOut(b,uid("MANAGER"))));assertEquals(1,stayState(b));invariants(b);
    }
    @Test void feeWinsCheckoutSeesUnconfirmedConsumption() {
        long b=checkIn();pay(b,"300");clock.day(3);
        businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->folios.addItem(sid(b),fee("extra","1"),uid("MANAGER")),()->bookings.checkOut(b,uid("MANAGER"))));invariants(b);
    }
    @Test void roomChangeWinsCheckoutChecksLatestHistoryAndDatePolicy() {
        long b=checkIn();pay(b,"450");
        businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->change(b,room3),()->bookings.checkOut(b,uid("MANAGER"))));
        assertEquals(room3,jdbc.queryForObject("SELECT room_id FROM stay_room_assignment WHERE stay_id=? AND end_time IS NULL",Long.class,sid(b)));invariants(b);
        clock.day(3);bookings.checkOut(b,uid("MANAGER"));invariants(b);
    }
    @ParameterizedTest @ValueSource(booleans={true,false}) void oldSnapshotPaymentOrSummarySeesNewCommittedFee(boolean recordPayment) {
        long b=checkIn();CountDownLatch snapshot=new CountDownLatch(1),committed=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=worker(pool,"rr-fee-reader",()->{
                var tx=new TransactionTemplate(txManager);tx.setIsolationLevel(4);tx.executeWithoutResult(status->{
                    assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM folio_item WHERE folio_id=?",Integer.class,folio(b)));
                    snapshot.countDown();SqlGate.await(committed);
                    assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM folio_item WHERE folio_id=?",Integer.class,folio(b)));
                    if(recordPayment)pay(b,"5");else financial.recalculateSummary(folio(b));
                });
            });SqlGate.await(snapshot);assertNull(result(worker(pool,"fee-writer",()->folios.addItem(sid(b),fee("committed","10"),uid("MANAGER")))));
            committed.countDown();assertNull(result(first));
        }finally{committed.countDown();stop(pool);}assertEquals(310,queries.byBooking(b,uid("CUSTOMER")).totalAmount().intValueExact());invariants(b);
    }
}
