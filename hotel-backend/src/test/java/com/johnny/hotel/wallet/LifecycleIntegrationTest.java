package com.johnny.hotel.wallet;
import com.johnny.hotel.support.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleIntegrationTest extends FinancialDevelopmentFixture {
    @org.springframework.beans.factory.annotation.Autowired com.johnny.hotel.booking.deposit.DepositService deposits;
    long create(){return createBooking("CUSTOMER");}
    void approve(long b){var r=new ApproveBookingRequest();r.setAssignedRoomId(room1);bookings.approveBooking(b,r,uid("MANAGER"));register(b);}
    long checkIn(){return stay();}
    int state(long b){return bookingState(b);}
    void change(long b,long room){var r=new ChangeRoomDuringStayRequest();r.setNewRoomId(room);r.setReason("test move");bookings.changeRoomDuringStay(b,r,uid("MANAGER"));}
    FolioItemCommand fee(String key,String amount){return FolioItemCommand.builder().itemType("SERVICE_CHARGE").description("test service").businessDate(arrival).quantity(BigDecimal.ONE).unitPrice(new BigDecimal(amount)).amount(new BigDecimal(amount)).eventKey(key).build();}
    @Override void pay(long b,String amount){super.pay(b,amount);}
    void invariants(long b){if(jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b)==0){assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio f JOIN stay s ON s.id=f.stay_id WHERE s.booking_id=?",Integer.class,b));return;} if(!jdbc.queryForObject("SELECT user_id FROM booking WHERE id=?",Long.class,b).equals(uid("CUSTOMER")))return;invariantBooking(b);}

    @Test void createHasOneContractSnapshotAndFullDeposit() {
        long b=create();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        as("STAFF");assertEquals(300,deposits.summary(b).balance().intValueExact());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM booking_nightly_rate WHERE booking_id=?",Integer.class,b));
        assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        invariants(b);
    }
    @Test void createFailureRollsBackEveryTable() {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM booking",Integer.class);
        gate.arm(Thread.currentThread().getName(),"DepositMapper.open",true);
        assertThrows(Exception.class,this::create);gate.clear();
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM booking",Integer.class));
    }
    @Test void fundedPortalReservationCannotBeRepricedByDateEdit() {
        long b=create();jdbc.update("UPDATE room_type SET base_price=120 WHERE id="+type1);
        var r=new UpdateBookingRequest();r.setRoomTypeId(type1);r.setGuestCount(2);r.setCheckInDate(arrival.plusDays(1));r.setCheckOutDate(arrival.plusDays(4));
        assertThrows(BusinessException.class,()->bookings.updateBooking(b,r,uid("CUSTOMER")));
        assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=?",Integer.class,b));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=? AND is_active=1",Integer.class,b));
        invariants(b);
    }
    @Test void approvedFundedReservationCannotChangeRoomTypeWithoutDepositAdjustment() {
        long b=create();approve(b);
        var r=new ChangeBookingRoomTypeRequest();r.setNewRoomTypeId(type2);r.setNewRoomId(room3);
        assertThrows(BusinessException.class,()->bookings.changeRoomType(b,r,uid("MANAGER")));
        assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        assertEquals(room1,jdbc.queryForObject("SELECT reserved_room_id FROM booking WHERE id=?",Long.class,b));
    }
    @Test void legacyFundedCancellationRequiresExplicitPolicyAndRejectKeepsDeposit() {
        long b=create();approve(b);
        assertThrows(BusinessException.class,()->bookings.cancelBooking(b,uid("CUSTOMER")));
        assertEquals(1,state(b));
        long other=create();bookings.rejectBooking(other,uid("MANAGER"));
        assertThrows(BusinessException.class,()->approve(other));assertEquals(300,deposits.summary(other).balance().intValueExact());invariants(other);
    }
    @Test void checkInPostsExactSnapshotAndCannotRepeatOrCancel() {
        long b=create();approve(b);bookings.checkIn(b,uid("MANAGER"));
        assertEquals(3,queries.byBooking(b,uid("CUSTOMER")).items().size());
        assertEquals(0,queries.byBooking(b,uid("CUSTOMER")).balanceAmount().intValueExact());
        assertThrows(BusinessException.class,()->bookings.checkIn(b,uid("MANAGER")));
        assertThrows(BusinessException.class,()->bookings.cancelBooking(b,uid("CUSTOMER")));
        assertThrows(BusinessException.class,()->bookings.cancelBookingByAdmin(b,uid("MANAGER")));invariants(b);
    }
    @ParameterizedTest @ValueSource(ints={-1,1,3}) void checkInRejectsUnsupportedDate(int day) {
        long b=create();approve(b);clock.day(day);assertThrows(BusinessException.class,()->bookings.checkIn(b,uid("MANAGER")));assertEquals(1,state(b));invariants(b);
    }
    @Test void missingNightRejectsCheckInWithoutPartialState() {
        long b=create();approve(b);jdbc.update("DELETE FROM booking_nightly_rate WHERE booking_id=? AND stay_date=?",b,arrival);
        assertThrows(BusinessException.class,()->bookings.checkIn(b,uid("MANAGER")));assertEquals(1,state(b));invariants(b);
    }
    @Test void wrongTotalAndCurrencyRejectCheckIn() {
        long b=create();approve(b);jdbc.update("UPDATE booking SET total_price=301 WHERE id=?",b);
        assertThrows(BusinessException.class,()->bookings.checkIn(b,uid("MANAGER")));
        jdbc.update("UPDATE booking SET total_price=300 WHERE id=?",b);jdbc.update("UPDATE reservation_deposit_account SET currency='USD' WHERE booking_id=?",b);
        assertThrows(BusinessException.class,()->bookings.checkIn(b,uid("MANAGER")));assertEquals(1,state(b));
    }
    @Test void sameTypeCrossTypeAndSameDayReturnPreserveLedgerAndContract() {
        long b=checkIn();clock.day(1);change(b,room2);assertEquals(300,queries.byBooking(b,uid("CUSTOMER")).totalAmount().intValueExact());
        change(b,room3);assertEquals(400,queries.byBooking(b,uid("CUSTOMER")).totalAmount().intValueExact());
        assertThrows(BusinessException.class,()->rooms.setRoomAvailable(room1));
        assertEquals(400,queries.byBooking(b,uid("CUSTOMER")).totalAmount().intValueExact());
        assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        assertEquals(type1,jdbc.queryForObject("SELECT room_type_id FROM booking WHERE id=?",Long.class,b));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM stay_room_assignment WHERE stay_id=(SELECT id FROM stay WHERE booking_id=?)",Integer.class,b));
        pay(b,"100");clock.day(3);bookings.checkOut(b,uid("MANAGER"));invariants(b);
    }
    @Test void roomBillingRetryIsIdempotentAndRejectsForgedAttribution() {
        long b=checkIn();clock.day(1);change(b,room3);
        var rows=jdbc.queryForList("SELECT * FROM stay_room_assignment WHERE stay_id=(SELECT id FROM stay WHERE booking_id=?) ORDER BY id",b);
        long old=((Number)rows.get(0).get("id")).longValue(),next=((Number)rows.get(1).get("id")).longValue();
        var c=RoomChangeBillingCommand.builder().stayId(sid(b)).oldAssignmentId(old).newAssignmentId(next).oldRoomId(room1).newRoomId(room3)
                .oldRoomTypeId(type1).newRoomTypeId(type2).changeDate(arrival.plusDays(1)).checkOutDate(arrival.plusDays(3)).operatorId(uid("MANAGER")).build();
        int count=queries.byBooking(b,uid("CUSTOMER")).items().size();folios.applyRoomChangeBilling(c);assertEquals(count,queries.byBooking(b,uid("CUSTOMER")).items().size());
        c.setNewRoomTypeId(type1);assertThrows(BusinessException.class,()->folios.applyRoomChangeBilling(c));invariants(b);
    }
    @Test void roomMaintenanceCannotBypassLiveStayAndMetadataCannotChange() {
        long b=checkIn();
        assertThrows(BusinessException.class,()->rooms.setRoomMaintenance(room1));assertThrows(BusinessException.class,()->rooms.setRoomAvailable(room1));
        assertThrows(BusinessException.class,()->rooms.disableRoom(room1));
        var r=new RoomRequest();r.setRoomNumber("T101");r.setRoomTypeId(type2);r.setFloor(1);
        assertThrows(BusinessException.class,()->rooms.updateRoom(room1,r));
        rooms.setRoomMaintenance(room2);rooms.setRoomAvailable(room2);rooms.disableRoom(room2);rooms.enableRoom(room2);invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"debt","credit"}) void checkoutBlocksDebtOrCredit(String kind) {
        long b=checkIn();
        if(kind.equals("debt")){as("MANAGER");var e=expenses.register(folio(b),expense("800"));expenses.confirm(folio(b),e.id());}
        else pay(b,"1");
        clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,uid("MANAGER")));assertEquals(1,stayState(b));assertNull(queries.byBooking(b,uid("CUSTOMER")).closedTime());invariants(b);
    }
    @Test void checkoutClosesLastAssignmentAndBlocksNewFinancialWritesButAllowsPaymentRetry() {
        long b=checkIn();clock.day(3);bookings.checkOut(b,uid("MANAGER"));
        assertThrows(BusinessException.class,()->pay(b,"1"));assertThrows(BusinessException.class,()->folios.addItem(sid(b),fee("late","1"),uid("MANAGER")));
        assertThrows(BusinessException.class,()->bookings.checkOut(b,uid("MANAGER")));invariants(b);
    }
    @ParameterizedTest @ValueSource(ints={0,2,4}) void checkoutRejectsUnspecifiedEarlyOrLatePolicy(int day) {
        long b=checkIn();clock.day(day);assertThrows(BusinessException.class,()->bookings.checkOut(b,uid("MANAGER")));invariants(b);
    }
    @Test void zeroBalanceCannotHideMissingRoomCharge() {
        long b=checkIn();jdbc.update("DELETE FROM folio_item WHERE folio_id=? ORDER BY id LIMIT 1",folio(b));financial.recalculateSummary(folio(b));clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,uid("MANAGER")));invariants(b);
    }
    @Test void extraConsumptionIsNotPretendedConfirmed() {
        long b=checkIn();folios.addItem(sid(b),fee("service:1","10"),uid("MANAGER"));pay(b,"10");clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,uid("MANAGER")));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"room","assignment","event","reversal"}) void checkoutDetectsCorruptHistory(String kind) {
        long b=checkIn();clock.day(1);change(b,room3);pay(b,"100");clock.day(3);
        switch(kind) {
            case "room" -> jdbc.update("UPDATE room SET status=1 WHERE id="+room3);
            case "assignment" -> jdbc.update("UPDATE stay_room_assignment SET room_type_id=? WHERE stay_id=? AND end_time IS NULL",type1,sid(b));
            case "event" -> jdbc.update("DELETE FROM room_billing_event WHERE stay_id=(SELECT id FROM stay WHERE booking_id=?)",b);
            case "reversal" -> {jdbc.update("DELETE FROM folio_item WHERE folio_id=? AND item_type='ROOM_RATE_ADJUSTMENT' ORDER BY id LIMIT 1",folio(b));financial.recalculateSummary(folio(b));}
        }
        assertThrows(BusinessException.class,()->bookings.checkOut(b,uid("MANAGER")));assertEquals(1,stayState(b));assertNull(queries.byBooking(b,uid("CUSTOMER")).closedTime());
    }
    @ParameterizedTest @ValueSource(strings={"checkin","change","checkout","payment"}) void auditFailureRollsBackEntireOperation(String operation) {
        long b=create();approve(b);
        if(!operation.equals("checkin"))bookings.checkIn(b,uid("MANAGER"));
        if(operation.equals("checkout")){clock.day(3);}
        var before=operation.equals("checkin")?null:queries.byBooking(b,uid("CUSTOMER"));int prior=state(b);int audits=jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class);
        gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);
        assertThrows(Exception.class,()->{switch(operation){case "checkin"->bookings.checkIn(b,uid("MANAGER"));case "change"->change(b,room3);case "checkout"->bookings.checkOut(b,uid("MANAGER"));default->pay(b,"10");}});
        if(before==null){assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));return;}
        var after=queries.byBooking(b,uid("CUSTOMER"));assertEquals(prior,state(b));assertEquals(before.totalAmount(),after.totalAmount());assertEquals(before.paidAmount(),after.paidAmount());
        assertEquals(before.closedTime(),after.closedTime());assertEquals(before.items().size(),after.items().size());assertEquals(audits,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class));invariants(b);
    }
    @Test void sourceAndAssignmentCannotCrossAccountsAndCreditsCannotExceedSource() {
        long b=checkIn(),other=stay("OTHER_CUSTOMER");var source=folios.addItem(sid(b),fee("svc","10"),uid("MANAGER"));
        var credit=fee("credit","-11");credit.setItemType("DISCOUNT");credit.setSourceItemId(source.getId());
        assertThrows(BusinessException.class,()->folios.addItem(sid(b),credit,uid("MANAGER")));
        credit.setAmount(new BigDecimal("-5"));credit.setUnitPrice(credit.getAmount());assertThrows(BusinessException.class,()->folios.addItem(sid(other),credit,uid("MANAGER")));
        folios.addItem(sid(b),credit,uid("MANAGER"));credit.setEventKey("credit2");credit.setAmount(new BigDecimal("-6"));credit.setUnitPrice(credit.getAmount());
        assertThrows(BusinessException.class,()->folios.addItem(sid(b),credit,uid("MANAGER")));
        var c=fee("foreign","1");c.setRoomAssignmentId(jdbc.queryForObject("SELECT id FROM stay_room_assignment WHERE stay_id=(SELECT id FROM stay WHERE booking_id=?)",Long.class,b));c.setRoomId(room1);c.setRoomTypeId(type1);
        assertThrows(BusinessException.class,()->folios.addItem(sid(other),c,uid("MANAGER")));invariants(b);invariants(other);
    }
    @Test void genericFeeRetryComparesContentAndKeepsSingleRow() {
        long b=checkIn();var c=fee("service:1","10");var first=folios.addItem(sid(b),c,uid("MANAGER"));assertEquals(first.getId(),folios.addItem(sid(b),c,uid("MANAGER")).getId());
        c.setDescription("different");assertThrows(BusinessException.class,()->folios.addItem(sid(b),c,uid("MANAGER")));invariants(b);
    }
    @Test void roomMaintenanceRejectsAnOuterRepeatableReadTransaction() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(txManager);tx.setIsolationLevel(4);
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->rooms.setRoomMaintenance(room1)));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM room WHERE id="+room1,Integer.class));
    }
    @Test void fundedContractCurrencyCannotBeChangedByReprice() {
        long b=create();approve(b);jdbc.update("UPDATE booking_price_version SET currency='USD' WHERE booking_id=?",b);jdbc.update("UPDATE reservation_deposit_account SET currency='USD' WHERE booking_id=?",b);
        var r=new ChangeBookingRoomTypeRequest();r.setNewRoomTypeId(type2);r.setNewRoomId(room3);assertThrows(BusinessException.class,()->bookings.changeRoomType(b,r,uid("MANAGER")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=?",Integer.class,b));
        assertEquals("USD",jdbc.queryForObject("SELECT currency FROM reservation_deposit_account WHERE booking_id=?",String.class,b));invariants(b);
    }
}
