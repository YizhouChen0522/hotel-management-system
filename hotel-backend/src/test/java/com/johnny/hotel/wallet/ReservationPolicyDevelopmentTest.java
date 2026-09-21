package com.johnny.hotel.wallet;

import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.guest.GuestRequests;
import com.johnny.hotel.reservation.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

class ReservationPolicyDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired ReservationPolicyService policyService;
    @Autowired StaffDirectService direct;

    ReservationPolicyRequests.Band band(int min,Integer max,String percent){return ReservationPolicyRequests.Band.builder()
            .minLeadDays(min).maxLeadDays(max).refundPercent(new BigDecimal(percent)).build();}
    ReservationPolicyRequests.Save policy(String name,String near,String far){return ReservationPolicyRequests.Save.builder().name(name)
            .bands(List.of(band(0,4,near),band(5,null,far))).build();}
    Long activate(String suffix,String near,String far){as("OWNER");var p=policyService.create(policy(run+suffix,near,far));
        policyService.validate(p.policy().getId());return policyService.activate(p.policy().getId()).policy().getId();}
    StaffDirectRequests.Create request(String suffix){return StaffDirectRequests.Create.builder().requestKey(run+suffix)
            .bookerProfile(GuestRequests.Profile.builder().firstName("Booker").lastName("Detached").documentNumber(run+suffix).build())
            .primaryProfile(GuestRequests.Profile.builder().firstName("Actual").lastName("Guest").documentNumber(run+suffix+"P").build())
            .roomTypeId(type1).reservedRoomId(room1).guestCount(2).checkInDate(arrival).checkOutDate(arrival.plusDays(3))
            .paymentMethod("CASH").receiptReference(run+suffix+"-receipt").build();}

    @Test void policyRequiresContiguousDayZeroAndValidPercent(){as("OWNER");
        var gap=policy("gap","0","100");gap.setBands(List.of(band(0,3,"0"),band(5,null,"100")));
        assertThrows(BusinessException.class,()->policyService.create(gap));
        var overlap=policy("overlap","0","100");overlap.setBands(List.of(band(0,5,"0"),band(5,null,"100")));
        assertThrows(BusinessException.class,()->policyService.create(overlap));
        var day0=policy("day0","0","100");day0.setBands(List.of(band(1,null,"100")));
        assertThrows(BusinessException.class,()->policyService.create(day0));
        assertThrows(BusinessException.class,()->policyService.create(policy("percent","101","100")));
        var valid=policyService.create(ReservationPolicyRequests.Save.builder().name(run+"multi")
                .bands(List.of(band(0,0,"0"),band(1,2,"25"),band(3,9,"50"),band(10,null,"80"))).build());
        assertEquals(4,policyService.activate(valid.policy().getId()).bands().size());
    }

    @Test void policyHttpReadAndWriteRolesRemainSeparated() throws Exception {
        mvc.perform(get("/api/admin/reservation-policies").with(authentication(auth("STAFF"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reservation-policies").with(authentication(auth("HR_ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reservation-policies").with(authentication(auth("STAFF")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Not authorized\",\"bands\":[{\"minLeadDays\":0,\"refundPercent\":100}] }"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reservation-policies").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test void policyVersionsAreBoundToExistingBookings(){Long first=activate("first","10","80");
        long a=createBooking("CUSTOMER");Long second=activate("second","20","40");long b=createBooking("OTHER_CUSTOMER");
        assertEquals(first,jdbc.queryForObject("SELECT reservation_policy_id FROM booking WHERE id=?",Long.class,a));
        assertEquals(second,jdbc.queryForObject("SELECT reservation_policy_id FROM booking WHERE id=?",Long.class,b));
        as("OWNER");assertEquals(0,new BigDecimal("80").compareTo(policyService.percent(first,11)));
        assertThrows(BusinessException.class,()->policyService.edit(first,policy("illegal","0","0")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reservation_policy WHERE status=1",Integer.class));
    }

    @Test void staffDirectRequiresActivePolicy(){as("STAFF");var r=request("no-policy");
        assertThrows(BusinessException.class,()->direct.create(r));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_direct_request_key=?",Integer.class,r.getRequestKey()));}

    @Test void directCannotConfirmWithoutConcreteRoomOrSuccessfulFullReceipt(){activate("guarantee","0","100");as("STAFF");
        var noRoom=request("no-room");noRoom.setReservedRoomId(null);
        assertThrows(BusinessException.class,()->direct.create(noRoom));
        var invalidReceipt=request("bad-method");invalidReceipt.setPaymentMethod("NOT_A_PAYMENT_METHOD");
        assertThrows(BusinessException.class,()->direct.create(invalidReceipt));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_direct_request_key IN (?,?)",Integer.class,
                noRoom.getRequestKey(),invalidReceipt.getRequestKey()));
        as("CUSTOMER");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->direct.create(request("customer-denied")));
    }

    @Test void staffDirectReceiptQuoteIdentityAndRetry(){activate("direct","0","100");as("STAFF");
        var r=request("direct01");var x=direct.create(r);bookingIds.add(x.bookingId());
        assertEquals(x.bookingId(),direct.create(r).bookingId());assertEquals(1,x.status());
        assertEquals(0,x.acceptedQuote().compareTo(jdbc.queryForObject("SELECT amount FROM deposit_payment WHERE id=?",BigDecimal.class,x.depositPaymentId())));
        assertNull(jdbc.queryForObject("SELECT user_id FROM booking WHERE id=?",Long.class,x.bookingId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wallet w JOIN guest_profile g ON g.linked_user_id=w.user_id WHERE g.id=?",Integer.class,x.bookerGuestId()));
        assertNotEquals(x.bookerGuestId(),x.primaryGuestId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,x.bookingId()));
        var changed=request("direct01");changed.setReservedRoomId(room2);
        assertThrows(BusinessException.class,()->direct.create(changed));
        var changedGuest=request("direct01");changedGuest.getPrimaryProfile().setFirstName("Different");
        assertThrows(BusinessException.class,()->direct.create(changedGuest));
    }

    @Test void sameRoomNonoverlapWorksButOverlapAndMismatchFail(){activate("inventory","0","100");as("STAFF");
        var first=direct.create(request("first01"));bookingIds.add(first.bookingId());
        assertThrows(BusinessException.class,()->direct.create(request("overlap01")));
        var later=request("later01");later.setCheckInDate(arrival.plusDays(5));later.setCheckOutDate(arrival.plusDays(7));
        var second=direct.create(later);bookingIds.add(second.bookingId());assertEquals(room1,second.reservedRoomId());
        var mismatch=request("mismatch01");mismatch.setRoomTypeId(type2);
        assertThrows(BusinessException.class,()->direct.create(mismatch));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_direct_request_key LIKE ?",Integer.class,run+"%"));
    }

    @Test void concurrentOverlappingStaffReservationsHaveOnlyOneWinner() throws Exception {
        activate("race","0","100");
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            Callable<Boolean> first=()->{as("STAFF");start.await();try{return direct.create(request("raceA01"))!=null;}catch(BusinessException|DataAccessException e){return false;}};
            Callable<Boolean> second=()->{as("STAFF");start.await();try{return direct.create(request("raceB01"))!=null;}catch(BusinessException|DataAccessException e){return false;}};
            var a=pool.submit(first);var b=pool.submit(second);start.countDown();
            assertEquals(1,(a.get(25,TimeUnit.SECONDS)?1:0)+(b.get(25,TimeUnit.SECONDS)?1:0));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_direct_request_key LIKE ?",Integer.class,run+"race%"));
        } finally {pool.shutdownNow();}
    }

    @Test void concurrentPolicyActivationMaintainsSingleActiveVersion() throws Exception {
        as("OWNER");var a=policyService.create(policy(run+"raceA","10","90"));
        var b=policyService.create(policy(run+"raceB","20","80"));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Void> activateA=()->{as("OWNER");start.await();policyService.activate(a.policy().getId());return null;};
            Callable<Void> activateB=()->{as("OWNER");start.await();policyService.activate(b.policy().getId());return null;};
            var x=pool.submit(activateA);var y=pool.submit(activateB);start.countDown();
            x.get(25,TimeUnit.SECONDS);y.get(25,TimeUnit.SECONDS);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reservation_policy WHERE status=1",Integer.class));
        } finally {pool.shutdownNow();}
    }
}
