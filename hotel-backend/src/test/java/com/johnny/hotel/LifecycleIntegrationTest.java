package com.johnny.hotel;
import com.johnny.hotel.support.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests", matches="true")
class LifecycleIntegrationTest extends IsolatedMysqlTest {
    @Test void createHasOneContractSnapshotAndEmptyAccount() {
        long b=create(); var f=queries.byBooking(b,1L);
        assertEquals(0,f.totalAmount().signum()); assertEquals(1,f.status()); assertTrue(f.items().isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=? AND is_active=1",Integer.class,b));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM booking_nightly_rate WHERE booking_id=?",Integer.class,b));
        assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        assertEquals(f.id(),folios.ensureFolioExists(b).getId()); invariants(b);
    }
    @Test void createFailureRollsBackEveryTable() {
        gate.arm(Thread.currentThread().getName(),"FolioMapper.insert",true);
        assertThrows(Exception.class,this::create);
        for(String table:new String[]{"booking","booking_price_version","booking_nightly_rate","folio"})
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
    }
    @Test void dateEditPreservesOverlapAndPricesOnlyNewNights() {
        long b=create();jdbc.update("UPDATE room_type SET base_price=120 WHERE id=1");
        var r=new UpdateBookingRequest();r.setRoomTypeId(1L);r.setGuestCount(2);r.setCheckInDate(arrival.plusDays(1));r.setCheckOutDate(arrival.plusDays(4));
        assertEquals(320,bookings.updateBooking(b,r,1L).getTotalPrice().intValueExact());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=?",Integer.class,b));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=? AND is_active=1",Integer.class,b));
        invariants(b);
    }
    @Test void approvedTypeChangeRepricesContractButPreservesCurrencyAndPrepayment() {
        long b=create();approve(b);pay(b,"50");
        var r=new ChangeBookingRoomTypeRequest();r.setNewRoomTypeId(2L);r.setNewRoomId(3L);
        assertEquals(450,bookings.changeRoomType(b,r,2L).getTotalPrice().intValueExact());
        assertEquals(1,jdbc.queryForObject("SELECT status FROM room WHERE id=1",Integer.class));
        assertEquals(50,queries.byBooking(b,1L).paidAmount().intValueExact());invariants(b);
    }
    @Test void cancelAndRejectKeepPrepaidMoneyAndForbidRepeat() {
        long b=create();approve(b);pay(b,"20");bookings.cancelBooking(b,1L);
        assertEquals(4,state(b));assertEquals(1,jdbc.queryForObject("SELECT status FROM room WHERE id=1",Integer.class));
        assertThrows(BusinessException.class,()->bookings.cancelBookingByAdmin(b,2L));invariants(b);
        long other=create();pay(other,"15");bookings.rejectBooking(other,2L);
        assertThrows(BusinessException.class,()->approve(other));assertEquals(15,queries.byBooking(other,1L).paidAmount().intValueExact());invariants(other);
    }
    @Test void checkInPostsExactSnapshotAndCannotRepeatOrCancel() {
        long b=create();approve(b);pay(b,"100");bookings.checkIn(b,2L);
        assertEquals(3,queries.byBooking(b,1L).items().size());
        assertEquals(200,queries.byBooking(b,1L).balanceAmount().intValueExact());
        assertThrows(BusinessException.class,()->bookings.checkIn(b,2L));
        assertThrows(BusinessException.class,()->bookings.cancelBooking(b,1L));
        assertThrows(BusinessException.class,()->bookings.cancelBookingByAdmin(b,2L));invariants(b);
    }
    @ParameterizedTest @ValueSource(ints={-1,1,3}) void checkInRejectsUnsupportedDate(int day) {
        long b=create();approve(b);clock.day(day);assertThrows(BusinessException.class,()->bookings.checkIn(b,2L));assertEquals(1,state(b));invariants(b);
    }
    @Test void missingNightRejectsCheckInWithoutPartialState() {
        long b=create();approve(b);jdbc.update("DELETE FROM booking_nightly_rate WHERE booking_id=? AND stay_date=?",b,arrival);
        assertThrows(BusinessException.class,()->bookings.checkIn(b,2L));assertEquals(1,state(b));invariants(b);
    }
    @Test void wrongTotalAndCurrencyRejectCheckIn() {
        long b=create();approve(b);jdbc.update("UPDATE booking SET total_price=301 WHERE id=?",b);
        assertThrows(BusinessException.class,()->bookings.checkIn(b,2L));
        jdbc.update("UPDATE booking SET total_price=300 WHERE id=?",b);jdbc.update("UPDATE folio SET currency='USD' WHERE booking_id=?",b);
        assertThrows(BusinessException.class,()->bookings.checkIn(b,2L));assertEquals(1,state(b));
    }
    @Test void sameTypeCrossTypeAndSameDayReturnPreserveLedgerAndContract() {
        long b=checkIn();clock.day(1);change(b,2);assertEquals(300,queries.byBooking(b,1L).totalAmount().intValueExact());
        change(b,3);assertEquals(400,queries.byBooking(b,1L).totalAmount().intValueExact());
        jdbc.update("UPDATE room_type SET base_price=120 WHERE id=1");rooms.setRoomAvailable(1L);change(b,1);
        assertEquals(340,queries.byBooking(b,1L).totalAmount().intValueExact());
        assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        assertEquals(1,jdbc.queryForObject("SELECT room_type_id FROM booking WHERE id=?",Integer.class,b));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM booking_room_assignment WHERE booking_id=?",Integer.class,b));
        pay(b,"340");clock.day(3);bookings.checkOut(b,2L);invariants(b);
    }
    @Test void roomBillingRetryIsIdempotentAndRejectsForgedAttribution() {
        long b=checkIn();clock.day(1);change(b,3);
        var rows=jdbc.queryForList("SELECT * FROM booking_room_assignment WHERE booking_id=? ORDER BY id",b);
        long old=((Number)rows.get(0).get("id")).longValue(),next=((Number)rows.get(1).get("id")).longValue();
        var c=RoomChangeBillingCommand.builder().bookingId(b).oldAssignmentId(old).newAssignmentId(next).oldRoomId(1L).newRoomId(3L)
                .oldRoomTypeId(1L).newRoomTypeId(2L).changeDate(arrival.plusDays(1)).checkOutDate(arrival.plusDays(3)).operatorId(2L).build();
        int count=queries.byBooking(b,1L).items().size();folios.applyRoomChangeBilling(c);assertEquals(count,queries.byBooking(b,1L).items().size());
        c.setNewRoomTypeId(1L);assertThrows(BusinessException.class,()->folios.applyRoomChangeBilling(c));invariants(b);
    }
    @Test void roomMaintenanceCannotBypassLiveStayAndMetadataCannotChange() {
        long b=checkIn();
        assertThrows(BusinessException.class,()->rooms.setRoomMaintenance(1L));assertThrows(BusinessException.class,()->rooms.setRoomAvailable(1L));
        assertThrows(BusinessException.class,()->rooms.disableRoom(1L));assertThrows(BusinessException.class,()->rooms.setRoomOccupied(2L));
        assertThrows(BusinessException.class,()->rooms.setRoomBooked(2L));
        var r=new RoomRequest();r.setRoomNumber("T101");r.setRoomTypeId(2L);r.setFloor(1);
        assertThrows(BusinessException.class,()->rooms.updateRoom(1L,r));
        rooms.setRoomMaintenance(2L);rooms.setRoomAvailable(2L);rooms.disableRoom(2L);rooms.enableRoom(2L);invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"0","299","301"}) void checkoutBlocksDebtOrCredit(String amount) {
        long b=checkIn();if(!amount.equals("0"))pay(b,amount);clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));assertEquals(2,state(b));assertNull(queries.byBooking(b,1L).closedTime());invariants(b);
    }
    @Test void checkoutClosesLastAssignmentAndBlocksNewFinancialWritesButAllowsPaymentRetry() {
        long b=checkIn();var r=request("300");var p=payments.recordPayment(folio(b),r,2L);clock.day(3);bookings.checkOut(b,2L);
        assertEquals(p.getId(),payments.recordPayment(folio(b),r,2L).getId());
        assertThrows(BusinessException.class,()->pay(b,"1"));assertThrows(BusinessException.class,()->folios.addItem(b,fee("late","1"),2L));
        assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);
    }
    @ParameterizedTest @ValueSource(ints={0,2,4}) void checkoutRejectsUnspecifiedEarlyOrLatePolicy(int day) {
        long b=checkIn();pay(b,"300");clock.day(day);assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);
    }
    @Test void zeroBalanceCannotHideMissingRoomCharge() {
        long b=checkIn();jdbc.update("DELETE FROM folio_item WHERE folio_id=? ORDER BY id LIMIT 1",folio(b));financial.recalculateSummary(b);pay(b,"200");clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);
    }
    @Test void extraConsumptionIsNotPretendedConfirmed() {
        long b=checkIn();folios.addItem(b,fee("service:1","10"),2L);pay(b,"310");clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"room","assignment","event","reversal"}) void checkoutDetectsCorruptHistory(String kind) {
        long b=checkIn();clock.day(1);change(b,3);pay(b,"400");clock.day(3);
        switch(kind) {
            case "room" -> jdbc.update("UPDATE room SET status=1 WHERE id=3");
            case "assignment" -> jdbc.update("UPDATE booking SET assigned_room_id=4 WHERE id=?",b);
            case "event" -> jdbc.update("DELETE FROM room_billing_event WHERE booking_id=?",b);
            case "reversal" -> {jdbc.update("DELETE FROM folio_item WHERE folio_id=? AND item_type='ROOM_RATE_ADJUSTMENT' ORDER BY id LIMIT 1",folio(b));financial.recalculateSummary(b);pay(b,"100");}
        }
        assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));assertEquals(2,state(b));assertNull(queries.byBooking(b,1L).closedTime());
    }
    @ParameterizedTest @ValueSource(strings={"checkin","change","checkout","payment"}) void auditFailureRollsBackEntireOperation(String operation) {
        long b=create();approve(b);
        if(!operation.equals("checkin"))bookings.checkIn(b,2L);
        if(operation.equals("checkout")){pay(b,"300");clock.day(3);}
        var before=queries.byBooking(b,1L);int prior=state(b);int audits=jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class);
        gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);
        assertThrows(Exception.class,()->{switch(operation){case "checkin"->bookings.checkIn(b,2L);case "change"->change(b,3);case "checkout"->bookings.checkOut(b,2L);default->pay(b,"10");}});
        var after=queries.byBooking(b,1L);assertEquals(prior,state(b));assertEquals(before.totalAmount(),after.totalAmount());assertEquals(before.paidAmount(),after.paidAmount());
        assertEquals(before.closedTime(),after.closedTime());assertEquals(before.items().size(),after.items().size());assertEquals(audits,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class));invariants(b);
    }
    @Test void sourceAndAssignmentCannotCrossAccountsAndCreditsCannotExceedSource() {
        long b=checkIn(),other=create();var source=folios.addItem(b,fee("svc","10"),2L);
        var credit=fee("credit","-11");credit.setItemType("DISCOUNT");credit.setSourceItemId(source.getId());
        assertThrows(BusinessException.class,()->folios.addItem(b,credit,2L));
        credit.setAmount(new BigDecimal("-5"));credit.setUnitPrice(credit.getAmount());assertThrows(BusinessException.class,()->folios.addItem(other,credit,2L));
        folios.addItem(b,credit,2L);credit.setEventKey("credit2");credit.setAmount(new BigDecimal("-6"));credit.setUnitPrice(credit.getAmount());
        assertThrows(BusinessException.class,()->folios.addItem(b,credit,2L));
        var c=fee("foreign","1");c.setRoomAssignmentId(jdbc.queryForObject("SELECT id FROM booking_room_assignment WHERE booking_id=?",Long.class,b));c.setRoomId(1L);c.setRoomTypeId(1L);
        assertThrows(BusinessException.class,()->folios.addItem(other,c,2L));invariants(b);invariants(other);
    }
    @Test void genericFeeRetryComparesContentAndKeepsSingleRow() {
        long b=create();var c=fee("service:1","10");var first=folios.addItem(b,c,2L);assertEquals(first.getId(),folios.addItem(b,c,2L).getId());
        c.setDescription("different");assertThrows(BusinessException.class,()->folios.addItem(b,c,2L));invariants(b);
    }
    @Test void roomMaintenanceRejectsAnOuterRepeatableReadTransaction() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(txManager);tx.setIsolationLevel(4);
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->rooms.setRoomMaintenance(1L)));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM room WHERE id=1",Integer.class));
    }
    @Test void fullRepriceKeepsExistingContractCurrencyWhenHotelDefaultDiffers() {
        long b=create();approve(b);jdbc.update("UPDATE booking_price_version SET currency='USD' WHERE booking_id=?",b);jdbc.update("UPDATE folio SET currency='USD' WHERE booking_id=?",b);
        var r=new ChangeBookingRoomTypeRequest();r.setNewRoomTypeId(2L);r.setNewRoomId(3L);bookings.changeRoomType(b,r,2L);
        assertEquals("USD",jdbc.queryForObject("SELECT currency FROM booking_price_version WHERE booking_id=? AND is_active=1",String.class,b));
        assertEquals("USD",queries.byBooking(b,1L).currency());invariants(b);
    }
}
