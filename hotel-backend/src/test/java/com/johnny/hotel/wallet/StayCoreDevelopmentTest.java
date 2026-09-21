package com.johnny.hotel.wallet;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.stay.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class StayCoreDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired StayApplicationService actualStays;
    @Autowired StayGuestService actualGuests;
    @Autowired DepositService deposits;
    @Autowired DepositTransferService transfers;
    @Autowired org.flywaydb.core.Flyway flyway;
    long approved(){long b=createBooking("CUSTOMER");var r=new ApproveBookingRequest();r.setAssignedRoomId(room1);bookings.approveBooking(b,r,uid("MANAGER"));register(b);return b;}
    void receive(long b,String amount){as("STAFF");deposits.receive(b,DepositRequests.Receive.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").requestKey("receipt_001").build());}
    @Test void reservationHasNoStayOrFolioAndMigrationValidates(){
        flyway.validate();long b=approved();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio f JOIN stay s ON s.id=f.stay_id WHERE s.booking_id=?",Integer.class,b));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='folio' AND column_name='booking_id'",Integer.class));
    }
    @Test void checkinCreatesActualGuestsAndTransfersImmutableDepositOnce(){
        long b=approved();var s=actualStays.checkIn(b,uid("MANAGER"));
        assertEquals(1,bookingState(b));assertEquals(1,s.getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stay_guest WHERE stay_id=?",Integer.class,s.getId()));
        assertEquals(new BigDecimal("0.00"),queries.byBooking(b,uid("CUSTOMER")).balanceAmount());
        new TransactionTemplate(txManager).executeWithoutResult(tx->transfers.transferForCheckIn(s.getId(),uid("MANAGER")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE folio_id=? AND payment_method='DEPOSIT_TRANSFER'",Integer.class,folio(b)));
        assertEquals(new BigDecimal("300.00"),jdbc.queryForObject("SELECT amount FROM deposit_payment WHERE account_id=(SELECT id FROM reservation_deposit_account WHERE booking_id=?)",BigDecimal.class,b));
        clock.day(3);actualStays.checkOut(s.getId(),uid("MANAGER"));invariantBooking(b);
    }
    @Test void failedCheckinRollsBackStayTransferAssignmentRoom(){
        long b=approved();
        gate.arm(Thread.currentThread().getName(),"DepositMapper.transfer",true);
        assertThrows(Exception.class,()->actualStays.checkIn(b,uid("MANAGER")));gate.clear();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        assertEquals(2,jdbc.queryForObject("SELECT status FROM room WHERE id=?",Integer.class,room1));
        as("STAFF");assertEquals(new BigDecimal("300.00"),deposits.summary(b).available());
    }
    @Test void concurrentCheckinCreatesExactlyOneStay(){
        long b=approved();var pool=Executors.newFixedThreadPool(2);
        try{var a=worker(pool,"arrival-a",()->actualStays.checkIn(b,uid("MANAGER")));var c=worker(pool,"arrival-b",()->actualStays.checkIn(b,uid("MANAGER")));var x=result(a);var y=result(c);assertTrue((x==null)!=(y==null));}
        finally{pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stay_room_assignment WHERE stay_id=?",Integer.class,sid(b)));
    }
    @Test void roomChangeUsesActualSegmentsAndPreservesReservation(){
        long b=stay();var r=new ChangeRoomDuringStayRequest();r.setNewRoomId(room2);r.setReason("Guest requested quieter room");
        actualStays.changeRoomDuringStay(sid(b),r,uid("MANAGER"));
        assertEquals(room1,jdbc.queryForObject("SELECT reserved_room_id FROM booking WHERE id=?",Long.class,b));
        assertEquals(room2,jdbc.queryForObject("SELECT room_id FROM stay_room_assignment WHERE stay_id=? AND end_time IS NULL",Long.class,sid(b)));
        clock.day(3);checkout(b);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM room_turnover_task WHERE stay_id=?",Integer.class,sid(b)));invariantBooking(b);
    }

    @Test void pendingDepositRefundPreventsArrivalUntilResolved(){
        long b=approved();as("CUSTOMER");
        var r=deposits.requestRefund(b,refundRequest("100","deposit_refund_1"));
        assertThrows(com.johnny.hotel.exception.BusinessException.class,()->actualStays.checkIn(b,uid("MANAGER")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        as("MANAGER");deposits.processRefund(b,r.getId(),process("deposit_process_1"),false);
        actualStays.checkIn(b,uid("MANAGER"));
        assertEquals(new BigDecimal("300.00"),queries.byBooking(b,uid("CUSTOMER")).paidAmount());
        clock.day(3);checkout(b);invariantBooking(b);
    }
    @Test void fullPortalDepositRejectsAdditionalReceiptAndArrivalTransfersExactlyOnce(){
        long b=approved();as("STAFF");assertThrows(com.johnny.hotel.exception.BusinessException.class,()->deposits.receive(b,DepositRequests.Receive.builder().amount(BigDecimal.ONE).paymentMethod("CASH").requestKey("new_receipt_1").build()));actualStays.checkIn(b,uid("MANAGER"));
        assertEquals(new BigDecimal("300.00"),queries.byBooking(b,uid("CUSTOMER")).paidAmount());
        as("STAFF");assertThrows(com.johnny.hotel.exception.BusinessException.class,()->deposits.receive(b,DepositRequests.Receive.builder().amount(BigDecimal.ONE).paymentMethod("CASH").requestKey("new_receipt_2").build()));
    }
    @Test void reservationAndActualGuestHaveSeparateOwnership()throws Exception{
        long b=approved();var actual=actualStays.checkIn(b,uid("MANAGER"));
        assertNull(jdbc.queryForObject("SELECT linked_user_id FROM guest_profile WHERE id=?",Long.class,actual.getPrimaryGuestId()));
        mvc.perform(get("/api/stays/{id}",actual.getId()).with(authentication(auth("CUSTOMER")))).andExpect(status().isOk());
        for(String suffix:java.util.List.of("","/guests","/assignments","/folio")){
            mvc.perform(get("/api/stays/"+actual.getId()+suffix).with(authentication(auth("OTHER_CUSTOMER")))).andExpect(status().isNotFound());
            mvc.perform(get("/api/stays/"+Long.MAX_VALUE+suffix).with(authentication(auth("OTHER_CUSTOMER")))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/stays").param("bookingId",String.valueOf(b)).with(authentication(auth("OTHER_CUSTOMER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/stays/{id}/folio",actual.getId()).with(authentication(auth("CUSTOMER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.stayId").value(actual.getId()));
    }
    @Test void stayApiRejectsCustomerHrAndAnonymousWrites()throws Exception{
        long b=approved();
        for(String role:java.util.List.of("CUSTOMER","HR_ADMIN"))mvc.perform(post("/api/bookings/{id}/check-in",b).with(authentication(auth(role)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings/{id}/check-in",b).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/{id}/check-in",b).with(authentication(auth("STAFF")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(b));
        for(String role:java.util.List.of("CUSTOMER","HR_ADMIN"))mvc.perform(post("/api/stays/{id}/check-out",sid(b)).with(authentication(auth(role)))).andExpect(status().isForbidden());
        assertEquals(1,bookingState(b));
    }
    @Test void concurrentRoomChangeThenCheckoutUsesCurrentAssignment(){
        long b=stay();clock.day(3);var move=new ChangeRoomDuringStayRequest();move.setNewRoomId(room2);move.setReason("Departure room move");
        assertNull(serialized("StayRoomAssignmentMapper.insert",()->actualStays.changeRoomDuringStay(sid(b),move,uid("MANAGER")),()->checkout(b)));
        assertEquals(2,stayState(b));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM room_turnover_task WHERE stay_id=?",Integer.class,sid(b)));
    }

    @Test void financeReadsButCannotMutateActualStay()throws Exception{
        long finance=users.registerEmployee(employeeRequest("FINANCE")).getId();created.add(finance);actors.put("FINANCE",finance);
        jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",finance);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='FINANCE'",finance);
        long b=approved();as("FINANCE");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->actualStays.checkIn(b,finance));
        actualStays.checkIn(b,uid("MANAGER"));mvc.perform(get("/api/stays/{id}",sid(b)).with(authentication(auth("FINANCE")))).andExpect(status().isOk());
        mvc.perform(get("/api/stays/{id}/folio",sid(b)).with(authentication(auth("FINANCE")))).andExpect(status().isOk());
        mvc.perform(post("/api/stays/{id}/check-out",sid(b)).with(authentication(auth("FINANCE")))).andExpect(status().isForbidden());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->actualStays.checkOut(sid(b),finance));
    }
    @Test void beingAnActualGuestDoesNotGrantReservationAccountOwnership()throws Exception{
        long b=createBooking("CUSTOMER");var approval=new ApproveBookingRequest();approval.setAssignedRoomId(room1);bookings.approveBooking(b,approval,uid("MANAGER"));
        var profile=guests.saveMe(uid("OTHER_CUSTOMER"),com.johnny.hotel.guest.GuestRequests.Profile.builder().firstName("Actual").lastName("Guest").nationality("CA").documentType("PASSPORT").documentNumber(run+"actual").build());
        guests.addToBooking(b,com.johnny.hotel.guest.GuestRequests.Add.builder().guestId(profile.id()).role(com.johnny.hotel.guest.GuestRole.PRIMARY).build(),uid("MANAGER"));
        guests.confirm(b,uid("MANAGER"));var stay=actualStays.checkIn(b,uid("MANAGER"));assertEquals(profile.id(),stay.getPrimaryGuestId());
        mvc.perform(get("/api/stays/{id}/folio",stay.getId()).with(authentication(auth("OTHER_CUSTOMER")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/stays/{id}/folio",stay.getId()).with(authentication(auth("CUSTOMER")))).andExpect(status().isOk());
    }
    @Test void reservationCancellationAndRejectionKeepDepositsOutsideStay(){
        long b=createBooking("CUSTOMER");bookings.rejectBooking(b,uid("MANAGER"));
        as("CUSTOMER");var refund=deposits.requestRefund(b,refundRequest("300","reject_refund_1"));as("MANAGER");deposits.processRefund(b,refund.getId(),process("reject_process_1"),true);
        assertEquals(5,bookingState(b));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
        assertEquals(new BigDecimal("1000.00"),wallets.find(wid("CUSTOMER")).getBalance());
    }
    private com.johnny.hotel.guest.GuestRequests.Add accompanying(String name){
        return com.johnny.hotel.guest.GuestRequests.Add.builder().role(com.johnny.hotel.guest.GuestRole.ACCOMPANYING)
                .profile(com.johnny.hotel.guest.GuestRequests.Profile.builder().firstName(name).lastName("Guest").build()).build();
    }
    @Test void concurrentActualGuestsCannotExceedContractCount(){
        long b=stay();
        assertInstanceOf(com.johnny.hotel.exception.BusinessException.class,
                serialized("StayMapper.addGuest",()->actualGuests.addAccompanying(sid(b),accompanying("First")),
                        ()->actualGuests.addAccompanying(sid(b),accompanying("Second"))));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM stay_guest WHERE stay_id=?",Integer.class,sid(b)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_guest WHERE booking_id=?",Integer.class,b));
    }
    @Test void checkoutPreventsWaitingActualGuestMutation(){
        long b=stay();clock.day(3);
        assertInstanceOf(com.johnny.hotel.exception.BusinessException.class,
                serialized("StayMapper.close",()->checkout(b),()->actualGuests.addAccompanying(sid(b),accompanying("Late"))));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stay_guest WHERE stay_id=?",Integer.class,sid(b)));
    }
}
