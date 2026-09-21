package com.johnny.hotel.wallet;

import com.johnny.hotel.dto.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.stay.RoomConflictReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortalDepositInventoryDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired RoomConflictReader conflicts;

    private CreateBookingRequest request(String role,Long type,LocalDate start,LocalDate end,String suffix){
        fundReservation(role);
        var r=new CreateBookingRequest();r.setRequestKey(run+suffix+UUID.randomUUID().toString().replace("-","").substring(0,8));
        r.setRoomTypeId(type);r.setGuestCount(1);r.setCheckInDate(start);r.setCheckOutDate(end);return r;
    }
    private long create(String role,Long type,LocalDate start,LocalDate end,String suffix){
        var r=request(role,type,start,end,suffix);long id=bookings.createBooking(r,uid(role)).getId();bookingIds.add(id);return id;
    }
    private void approve(long booking,long room){var a=new ApproveBookingRequest();a.setAssignedRoomId(room);bookings.approveBooking(booking,a,uid("MANAGER"));}

    @Test void portalCommitsFullWalletDepositAndSnapshotAtomicallyAndRetries() {
        var request=request("CUSTOMER",type1,arrival,arrival.plusDays(3),"full");
        BigDecimal before=jdbc.queryForObject("SELECT balance FROM wallet WHERE user_id=?",BigDecimal.class,uid("CUSTOMER"));
        var first=bookings.createBooking(request,uid("CUSTOMER"));bookingIds.add(first.getId());
        var replay=bookings.createBooking(request,uid("CUSTOMER"));assertEquals(first.getId(),replay.getId());
        BigDecimal quote=jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,first.getId());
        BigDecimal nights=jdbc.queryForObject("SELECT SUM(rate_amount) FROM booking_nightly_rate WHERE booking_id=? AND price_version_id=(SELECT id FROM booking_price_version WHERE booking_id=? AND is_active=1)",BigDecimal.class,first.getId(),first.getId());
        BigDecimal received=jdbc.queryForObject("SELECT SUM(p.amount) FROM deposit_payment p JOIN reservation_deposit_account a ON a.id=p.account_id WHERE a.booking_id=?",BigDecimal.class,first.getId());
        assertEquals(0,quote.compareTo(nights));assertEquals(0,quote.compareTo(received));
        assertEquals(0,before.subtract(quote).compareTo(jdbc.queryForObject("SELECT balance FROM wallet WHERE user_id=?",BigDecimal.class,uid("CUSTOMER"))));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transaction WHERE source_type='DEPOSIT_PAYMENT' AND request_key=?",Integer.class,request.getRequestKey()));
    }

    @Test void insufficientWalletRollsBackBookingSnapshotAndDeposit() {
        var r=new CreateBookingRequest();r.setRequestKey(run+"poor"+UUID.randomUUID().toString().replace("-","").substring(0,8));
        r.setRoomTypeId(type1);r.setGuestCount(1);r.setCheckInDate(arrival);r.setCheckOutDate(arrival.plusDays(3));
        assertThrows(BusinessException.class,()->bookings.createBooking(r,uid("CUSTOMER")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE portal_request_key=?",Integer.class,r.getRequestKey()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_payment WHERE request_key=?",Integer.class,r.getRequestKey()));
    }

    @Test void lockedNightlyRateIgnoresLaterMarketDecreaseAndIncrease() {
        long booking=create("CUSTOMER",type1,arrival,arrival.plusDays(1),"lock");
        BigDecimal locked=jdbc.queryForObject("SELECT rate_amount FROM booking_nightly_rate WHERE booking_id=?",BigDecimal.class,booking);
        jdbc.update("UPDATE room_type SET base_price=? WHERE id=?",locked.subtract(new BigDecimal("50.00")),type1);
        approve(booking,room1);register(booking);bookings.checkIn(booking,uid("MANAGER"));
        long folio=folio(booking);
        assertEquals(0,locked.compareTo(jdbc.queryForObject("SELECT amount FROM folio_item WHERE folio_id=? AND item_type='ROOM_CHARGE'",BigDecimal.class,folio)));
        assertEquals(0,locked.compareTo(jdbc.queryForObject("SELECT amount FROM payment WHERE folio_id=? AND payment_method='DEPOSIT_TRANSFER'",BigDecimal.class,folio)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refund WHERE folio_id=?",Integer.class,folio));
        jdbc.update("UPDATE room_type SET base_price=? WHERE id=?",locked.add(new BigDecimal("100.00")),type1);
        assertEquals(0,locked.compareTo(jdbc.queryForObject("SELECT amount FROM folio_item WHERE folio_id=? AND item_type='ROOM_CHARGE'",BigDecimal.class,folio)));
    }

    @Test void paidPendingReservationRejectsRepricingButAllowsCapacitySafeGuestCountChange() {
        long booking=create("CUSTOMER",type1,arrival,arrival.plusDays(3),"modify");
        var changed=new UpdateBookingRequest();changed.setRoomTypeId(type1);changed.setGuestCount(1);changed.setCheckInDate(arrival.plusDays(1));changed.setCheckOutDate(arrival.plusDays(4));
        assertThrows(BusinessException.class,()->bookings.updateBooking(booking,changed,uid("CUSTOMER")));
        changed.setCheckInDate(arrival);changed.setCheckOutDate(arrival.plusDays(3));
        assertEquals(1,bookings.updateBooking(booking,changed,uid("CUSTOMER")).getGuestCount());
    }

    @Test void nonOverlappingReservationsAndRoomChangeReleaseUseDateAndAssignmentTruth() {
        long a=create("CUSTOMER",type1,arrival,arrival.plusDays(7),"a");approve(a,room1);
        long b=create("OTHER_CUSTOMER",type1,arrival.plusDays(19),arrival.plusDays(23),"b");approve(b,room1);
        register(a);bookings.checkIn(a,uid("MANAGER"));
        var change=new ChangeRoomDuringStayRequest();change.setNewRoomId(room2);change.setReason("Operational room move");bookings.changeRoomDuringStay(a,change,uid("MANAGER"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay_room_assignment a JOIN stay s ON s.id=a.stay_id WHERE s.booking_id=? AND a.room_id=? AND a.end_time IS NULL",Integer.class,a,room1));
        assertTrue(conflicts.overlapping(room1,a,arrival.plusDays(4),arrival.plusDays(6)).isEmpty());
        long c=create("CUSTOMER",type1,arrival.plusDays(4),arrival.plusDays(6),"c");
        assertThrows(BusinessException.class,()->approve(c,room1));
        completeTurnoverAndInspect(room1);rooms.setRoomAvailable(room1);approve(c,room1);
        long overlapping=create("CUSTOMER",type1,arrival.plusDays(20),arrival.plusDays(22),"overlap");
        assertThrows(BusinessException.class,()->approve(overlapping,room1));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,b));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,c));
    }
}
