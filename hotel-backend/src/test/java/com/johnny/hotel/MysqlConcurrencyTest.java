package com.johnny.hotel;
import com.johnny.hotel.support.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests", matches="true")
class MysqlConcurrencyTest extends IsolatedMysqlTest {
    private void stop(ExecutorService pool) {
        pool.shutdown();
        try { if (!pool.awaitTermination(20,TimeUnit.SECONDS)) {pool.shutdownNow();fail("Concurrent workers did not finish");} }
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    private Future<Throwable> worker(ExecutorService pool,String name,Runnable action) {
        return pool.submit(()-> {Thread.currentThread().setName(name);try {action.run();return null;} catch(Throwable e){return e;} });
    }
    private Throwable result(Future<Throwable> future) {
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
    private Throwable serialized(String lockStatement,Runnable first,Runnable second) {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        gate.arm("first",lockStatement,false);
        try {
            Future<Throwable> a=worker(pool,"first",first);SqlGate.await(gate.reached);
            Future<Throwable> b=worker(pool,"second",second);databaseWaitObserved();gate.release.countDown();
            assertNull(result(a));return result(b);
        } finally {gate.clear();stop(pool);}
    }
    @Test void approveWinsRejectCannotOverwrite() {
        long b=create();businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->approve(b),()->bookings.rejectBooking(b,2L)));
        assertEquals(1,state(b));invariants(b);
    }
    @Test void rejectWinsApprovalCannotReserveRoom() {
        long b=create();businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->bookings.rejectBooking(b,2L),()->approve(b)));
        assertEquals(5,state(b));assertEquals(1,jdbc.queryForObject("SELECT status FROM room WHERE id=1",Integer.class));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"cancel","admin-cancel","repeat-checkin","checkout"}) void checkInSerializesLifecycle(String contender) {
        long b=create();approve(b);
        businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->bookings.checkIn(b,2L),()-> {
            switch(contender){case "cancel"->bookings.cancelBooking(b,1L);case "admin-cancel"->bookings.cancelBookingByAdmin(b,2L);case "repeat-checkin"->bookings.checkIn(b,2L);default->bookings.checkOut(b,2L);}
        }));assertEquals(2,state(b));invariants(b);
    }
    @Test void cancellationWinsCheckInCannotUseReleasedRoom() {
        long b=create();approve(b);businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->bookings.cancelBookingByAdmin(b,2L),()->bookings.checkIn(b,2L)));
        assertEquals(4,state(b));invariants(b);
    }
    @Test void roomMaintenanceWinsApprovalRejects() {
        long b=create();businessFailure(serialized("RoomMapper.selectByIdForUpdate",()->rooms.setRoomMaintenance(1L),()->approve(b)));
        assertEquals(0,state(b));assertEquals(3,jdbc.queryForObject("SELECT status FROM room WHERE id=1",Integer.class));invariants(b);
    }
    @Test void approvalWinsMaintenanceCannotReleaseReservation() {
        long b=create();businessFailure(serialized("RoomMapper.selectByIdForUpdate",()->approve(b),()->rooms.setRoomMaintenance(1L)));
        assertEquals(1,state(b));invariants(b);
    }
    @ParameterizedTest @ValueSource(booleans={true,false}) void concurrentPaymentsSameAndDifferentKeys(boolean sameKey) {
        long b=create();long f=folio(b);var a=request("10");var c=sameKey?a:request("15");
        assertNull(serialized("FolioMapper.selectByIdForUpdate",()->payments.recordPayment(f,a,2L),()->payments.recordPayment(f,c,2L)));
        assertEquals(sameKey?1:2,queries.byBooking(b,1L).payments().size());assertEquals(sameKey?10:25,queries.byBooking(b,1L).paidAmount().intValueExact());invariants(b);
    }
    @Test void concurrentSameKeyDifferentContentRejectsSecond() {
        long b=create();long f=folio(b);var a=request("10");var c=request("11");c.setIdempotencyKey(a.getIdempotencyKey());
        businessFailure(serialized("FolioMapper.selectByIdForUpdate",()->payments.recordPayment(f,a,2L),()->payments.recordPayment(f,c,2L)));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"checkin","change","add","summary"}) void outerRepeatableReadSnapshotCannotOverwriteCommittedPayment(String operation) {
        long b=operation.equals("change")?checkIn():create();if(operation.equals("checkin"))approve(b);
        if(operation.equals("change"))clock.day(1);
        CountDownLatch snapshot=new CountDownLatch(1),committed=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first=worker(pool,"rr-reader",()-> {
                var tx=new TransactionTemplate(txManager);tx.setIsolationLevel(4);
                tx.executeWithoutResult(status -> {
                    assertEquals("REPEATABLE-READ",jdbc.queryForObject("SELECT @@tx_isolation",String.class));
                    assertEquals(0,jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment WHERE folio_id=?",BigDecimal.class,folio(b)).signum());
                    snapshot.countDown();SqlGate.await(committed);
                    // Control reproduces BILL-006: ordinary SUM remains stale inside this outer transaction.
                    assertEquals(0,jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment WHERE folio_id=?",BigDecimal.class,folio(b)).signum());
                    switch(operation){case "checkin"->bookings.checkIn(b,2L);case "change"->change(b,3);case "add"->folios.addItem(b,fee("concurrent","20"),2L);default->financial.recalculateSummary(b);}
                });
            });
            SqlGate.await(snapshot);Future<Throwable> second=worker(pool,"payer",()->pay(b,"10"));assertNull(result(second));committed.countDown();assertNull(result(first));
        } finally {committed.countDown();stop(pool);}
        assertEquals(10,queries.byBooking(b,1L).paidAmount().intValueExact());invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"checkin","change","add"}) void bookingWriterPausedBeforeFolioDoesNotDeadlockPayment(String operation) {
        long b=operation.equals("change")?checkIn():create();if(operation.equals("checkin"))approve(b);
        var pool=Executors.newFixedThreadPool(2);gate.arm("booking-writer","BookingMapper.selectByIdForUpdate",false);
        try {
            var a=worker(pool,"booking-writer",()->{switch(operation){case "checkin"->bookings.checkIn(b,2L);case "change"->change(b,3);default->folios.addItem(b,fee("concurrent","10"),2L);}});
            SqlGate.await(gate.reached);var p=worker(pool,"payer",()->pay(b,"5"));assertNull(result(p));gate.release.countDown();assertNull(result(a));
        } finally {gate.clear();stop(pool);}invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"payment","add","change"}) void checkoutWinsFinalizationBlocksContenders(String operation) {
        long b=checkIn();pay(b,"300");clock.day(3);
        businessFailure(serialized("FolioMapper.selectByBookingIdForUpdate",()->bookings.checkOut(b,2L),()-> {
            switch(operation){case "payment"->pay(b,"1");case "add"->folios.addItem(b,fee("late","1"),2L);default->change(b,3);}
        }));assertEquals(3,state(b));invariants(b);
    }
    @Test void paymentWinsCheckoutSeesCreditAndCannotFinalize() {
        long b=checkIn();pay(b,"300");clock.day(3);
        businessFailure(serialized("FolioMapper.selectByIdForUpdate",()->pay(b,"1"),()->bookings.checkOut(b,2L)));assertEquals(2,state(b));invariants(b);
    }
    @Test void feeWinsCheckoutSeesUnconfirmedConsumption() {
        long b=checkIn();pay(b,"300");clock.day(3);
        businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->folios.addItem(b,fee("extra","1"),2L),()->bookings.checkOut(b,2L)));invariants(b);
    }
    @Test void roomChangeWinsCheckoutChecksLatestHistoryAndDatePolicy() {
        long b=checkIn();pay(b,"450");
        businessFailure(serialized("BookingMapper.selectByIdForUpdate",()->change(b,3),()->bookings.checkOut(b,2L)));
        assertEquals(3,jdbc.queryForObject("SELECT assigned_room_id FROM booking WHERE id=?",Integer.class,b));invariants(b);
        clock.day(3);bookings.checkOut(b,2L);invariants(b);
    }
    @ParameterizedTest @ValueSource(booleans={true,false}) void oldSnapshotPaymentOrSummarySeesNewCommittedFee(boolean recordPayment) {
        long b=create();CountDownLatch snapshot=new CountDownLatch(1),committed=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=worker(pool,"rr-fee-reader",()->{
                var tx=new TransactionTemplate(txManager);tx.setIsolationLevel(4);tx.executeWithoutResult(status->{
                    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio_item WHERE folio_id=?",Integer.class,folio(b)));
                    snapshot.countDown();SqlGate.await(committed);
                    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio_item WHERE folio_id=?",Integer.class,folio(b)));
                    if(recordPayment)pay(b,"5");else financial.recalculateSummary(b);
                });
            });SqlGate.await(snapshot);assertNull(result(worker(pool,"fee-writer",()->folios.addItem(b,fee("committed","10"),2L))));
            committed.countDown();assertNull(result(first));
        }finally{committed.countDown();stop(pool);}assertEquals(10,queries.byBooking(b,1L).totalAmount().intValueExact());invariants(b);
    }
}
