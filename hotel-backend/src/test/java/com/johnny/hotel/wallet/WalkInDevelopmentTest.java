package com.johnny.hotel.wallet;

import com.johnny.hotel.booking.deposit.DepositService;
import com.johnny.hotel.dto.RecordPaymentRequest;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.guest.*;
import com.johnny.hotel.stay.StayApplicationService;
import com.johnny.hotel.walkin.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalkInDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired WalkInBookingService walkIns;
    @Autowired StayApplicationService stays;
    @Autowired DepositService deposits;
    @Autowired com.johnny.hotel.wallet.RefundService refundService;

    GuestRequests.Profile profile(String first,String doc){return GuestRequests.Profile.builder().firstName(first).lastName("Walkin")
            .nationality("CA").documentType("PASSPORT").documentNumber(run+doc).build();}
    WalkInRequests.Create request(String key){return WalkInRequests.Create.builder().requestKey(run+key).bookerProfile(profile("Booker",key))
            .roomTypeId(type1).reservedRoomId(room1).guestCount(2).checkInDate(arrival).checkOutDate(arrival.plusDays(3)).build();}
    WalkInViews.Created create(String key){as("STAFF");var x=walkIns.create(request(key),uid("STAFF"));bookingIds.add(x.bookingId());return x;}

    @Test void migrationAndCustomerPortalReservationRemainValid(){
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='36' AND success=1",Integer.class));
        long booking=createBooking("CUSTOMER");
        var row=jdbc.queryForMap("SELECT user_id,booker_guest_profile_id,created_by_user_id,reservation_source FROM booking WHERE id=?",booking);
        assertEquals(uid("CUSTOMER"),((Number)row.get("user_id")).longValue());
        assertNotNull(row.get("booker_guest_profile_id"));
        assertEquals(uid("CUSTOMER"),((Number)row.get("created_by_user_id")).longValue());
        assertEquals("CUSTOMER_PORTAL",row.get("reservation_source"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reservation_deposit_account WHERE booking_id=?",Integer.class,booking));
    }

    @Test void staffCreatesConfirmedWalkInWithoutAccountWalletOrDeposit(){var x=create("walkin01");var b=jdbc.queryForMap("SELECT * FROM booking WHERE id=?",x.bookingId());
        assertNull(b.get("user_id"));assertEquals("WALK_IN",b.get("reservation_source"));assertEquals(uid("STAFF"),((Number)b.get("created_by_user_id")).longValue());
        assertEquals(x.booker().id(),((Number)b.get("booker_guest_profile_id")).longValue());assertNull(x.booker().linkedUserId());assertEquals(1,x.reservationStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reservation_deposit_account WHERE booking_id=?",Integer.class,x.bookingId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE ?",Integer.class,run+"%walk%"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_price_version WHERE booking_id=? AND is_active=1",Integer.class,x.bookingId()));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM booking_nightly_rate WHERE booking_id=?",Integer.class,x.bookingId()));}

    @Test void existingDetachedBookerCanDifferFromPrimaryGuest(){as("STAFF");var booker=guests.create(profile("Father","father"),uid("STAFF"));var r=request("walkin02");r.setBookerProfile(null);r.setBookerGuestId(booker.id());r.setPrimaryProfile(profile("Daughter","daughter"));
        var x=walkIns.create(r,uid("STAFF"));bookingIds.add(x.bookingId());assertEquals(booker.id(),x.booker().id());assertNotEquals(booker.id(),x.primaryGuest().id());
        var stay=stays.checkIn(x.bookingId(),uid("STAFF"));assertEquals(x.primaryGuest().id(),stay.getPrimaryGuestId());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stay_guest WHERE stay_id=? AND guest_id=?",Integer.class,stay.getId(),x.primaryGuest().id()));}

    @Test void invalidRolesAndFutureArrivalAreRejected()throws Exception{var r=request("walkin03");for(String role:java.util.List.of("CUSTOMER","HR_ADMIN")){as(role);assertThrows(AccessDeniedException.class,()->walkIns.create(r,uid(role)));}
        long finance=users.registerEmployee(employeeRequest("FINANCE")).getId();created.add(finance);actors.put("FINANCE",finance);jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",finance);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='FINANCE'",finance);as("FINANCE");assertThrows(AccessDeniedException.class,()->walkIns.create(r,finance));
        mvc.perform(post("/api/front-desk/walk-ins").with(authentication(auth("CUSTOMER"))).contentType("application/json")
                .content("{\"requestKey\":\"customer-denied-01\",\"bookerGuestId\":1,\"roomTypeId\":1,\"reservedRoomId\":1,\"guestCount\":1,\"checkInDate\":\"2026-10-01\",\"checkOutDate\":\"2026-10-02\"}"))
                .andExpect(status().isForbidden());
        as("STAFF");r.setRequestKey(run+"future01");r.setCheckInDate(arrival.plusDays(1));assertThrows(BusinessException.class,()->walkIns.create(r,uid("STAFF")));}

    @Test void roomConflictAndRequestRetryAreSafe(){var first=create("walkin04");as("STAFF");var same=walkIns.create(request("walkin04"),uid("STAFF"));assertEquals(first.bookingId(),same.bookingId());
        var other=request("walkin05");assertThrows(BusinessException.class,()->walkIns.create(other,uid("STAFF")));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE walk_in_request_key=?",Integer.class,run+"walkin04"));}

    @Test void concurrentRetryCreatesOneReservationAndProfiles()throws Exception{var r=request("parallel01");var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{as("STAFF");return walkIns.create(r,uid("STAFF"));});var b=pool.submit(()->{as("MANAGER");return walkIns.create(r,uid("MANAGER"));});var x=a.get(20,TimeUnit.SECONDS);var y=b.get(20,TimeUnit.SECONDS);bookingIds.add(x.bookingId());assertEquals(x.bookingId(),y.bookingId());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE walk_in_request_key=?",Integer.class,run+"parallel01"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_guest WHERE booking_id=?",Integer.class,x.bookingId()));}finally{pool.shutdownNow();}}

    @Test void depositWalletRefundAndCustomerIdorAreExplicitlyBlocked()throws Exception{var x=create("walkin06");as("STAFF");assertThrows(BusinessException.class,()->deposits.summary(x.bookingId()));var stay=stays.checkIn(x.bookingId(),uid("STAFF"));
        creditWallet("500");clock.day(3);assertThrows(BusinessException.class,()->stays.checkOut(stay.getId(),uid("STAFF")));assertEquals(new BigDecimal("500.00"),wallets.find(wid("CUSTOMER")).getBalance());
        as("MANAGER");assertThrows(BusinessException.class,()->refundService.list(folio(x.bookingId())));
        mvc.perform(get("/api/bookings/{id}",x.bookingId()).with(authentication(auth("CUSTOMER")))).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/stays/{id}",stay.getId()).with(authentication(auth("CUSTOMER")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/stays/{id}/folio",stay.getId()).with(authentication(auth("CUSTOMER")))).andExpect(status().isNotFound());}

    @Test void offlinePaymentSettlesAndChecksOutWithRealOperatorAudit(){var x=create("walkin07");var stay=stays.checkIn(x.bookingId(),uid("STAFF"));clock.day(3);assertThrows(BusinessException.class,()->stays.checkOut(stay.getId(),uid("STAFF")));
        payments.recordPayment(folio(x.bookingId()),RecordPaymentRequest.builder().amount(new BigDecimal("300")).paymentMethod("CASH").idempotencyKey(java.util.UUID.randomUUID().toString()).build(),uid("STAFF"));
        stays.checkOut(stay.getId(),uid("STAFF"));assertEquals(2,stayState(x.bookingId()));assertEquals(0,queries.byBookingForOperations(x.bookingId()).balanceAmount().signum());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE operator_id=? AND action IN ('CREATE_WALK_IN_BOOKING','RECORD_PAYMENT','CHECK_OUT')",Integer.class,uid("STAFF"))>=3);}
}
