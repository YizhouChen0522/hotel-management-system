package com.johnny.hotel.wallet;

import com.johnny.hotel.HotelBackendApplication;
import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.guest.GuestRequests;
import com.johnny.hotel.guest.GuestService;
import com.johnny.hotel.reservation.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.support.MutableHotelClock;
import com.johnny.hotel.support.WalletDevelopmentGuard;
import com.johnny.hotel.wallet.RefundRequests;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=HotelBackendApplication.class,properties={"hotel.wallet.dev.fixture=true","logging.level.org.springframework=WARN"})
@Import(ReservationSettlementDevelopmentTest.TimeConfig.class)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class ReservationSettlementDevelopmentTest {
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableHotelClock reservationClock(){return new MutableHotelClock();}}
    @DynamicPropertySource static void development(DynamicPropertyRegistry r){var e=WalletDevelopmentGuard.settings();
        r.add("spring.datasource.url",WalletDevelopmentGuard::url);r.add("spring.datasource.username",()->e.get("DB_USERNAME"));
        r.add("spring.datasource.password",()->e.get("DB_PASSWORD"));r.add("spring.data.redis.host",()->"localhost");
        r.add("spring.data.redis.port",()->6379);r.add("spring.data.redis.password",()->e.get("REDIS_PASSWORD"));
        r.add("hotel.redis.prefix",()->"hotel:v38-test:");}
    @Autowired JdbcTemplate jdbc;
    @Autowired SysUserService users;
    @Autowired BookingService bookings;
    @Autowired DepositService deposits;
    @Autowired ReservationPolicyService policies;
    @Autowired StaffDirectService direct;
    @Autowired GuestService guests;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired MutableHotelClock clock;
    final String run="v38_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
    final Map<String,Long> actors=new HashMap<>();
    long room,type;
    @BeforeEach void setup(){clock.day(0);for(String role:List.of("CUSTOMER","OTHER_CUSTOMER","STAFF","MANAGER","OWNER")){
        String n=run+role.toLowerCase(Locale.ROOT);long id;
        if(role.contains("CUSTOMER")){var r=new RegisterCustomerRequest();r.setUsername(n);r.setEmail(n+"@example.test");r.setPassword("v38-test-only");r.setRealName("Reservation test");id=users.registerCustomer(r).getId();}
        else{var r=new RegisterEmployeeRequest();r.setUsername(n);r.setEmail(n+"@example.test");r.setPassword("v38-test-only");r.setRealName("Reservation test");r.setApplyRoleCode(role);id=users.registerEmployee(r).getId();jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",id);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);}
        actors.put(role,id);
    }
        jdbc.update("INSERT INTO room_type(type_name,base_price,capacity) VALUES(?,100,4)",run);
        type=jdbc.queryForObject("SELECT id FROM room_type WHERE type_name=?",Long.class,run);
        jdbc.update("INSERT INTO room(room_number,room_type_id,floor,status) VALUES(?,?,1,1)",run,type);
        room=jdbc.queryForObject("SELECT id FROM room WHERE room_number=?",Long.class,run);
    }
    @AfterEach void clearAuth(){SecurityContextHolder.clearContext();}
    long id(String role){return actors.get(role);}
    void as(String role){var token=new UsernamePasswordAuthenticationToken("test",null,List.of(new SimpleGrantedAuthority("ROLE_"+(role.equals("OTHER_CUSTOMER")?"CUSTOMER":role))));token.setDetails(id(role));SecurityContextHolder.getContext().setAuthentication(token);}
    void policy(String near,String far){as("OWNER");var save=ReservationPolicyRequests.Save.builder().name(run).bands(List.of(
            ReservationPolicyRequests.Band.builder().minLeadDays(0).maxLeadDays(4).refundPercent(new BigDecimal(near)).build(),
            ReservationPolicyRequests.Band.builder().minLeadDays(5).refundPercent(new BigDecimal(far)).build())).build();
        policies.activate(policies.create(save).policy().getId());}
    long portal(int arrivalDay){var r=new CreateBookingRequest();r.setRoomTypeId(type);r.setGuestCount(1);r.setCheckInDate(LocalDate.of(2026,10,1).plusDays(arrivalDay));r.setCheckOutDate(r.getCheckInDate().plusDays(3));return bookings.createBooking(r,id("CUSTOMER")).getId();}
    StaffDirectRequests.Create request(String suffix,int arrivalDay){return StaffDirectRequests.Create.builder().requestKey(run+suffix)
            .bookerProfile(GuestRequests.Profile.builder().firstName("Detached").lastName("Booker").documentNumber(run+suffix).build())
            .roomTypeId(type).reservedRoomId(room).guestCount(1).checkInDate(LocalDate.of(2026,10,1).plusDays(arrivalDay))
            .checkOutDate(LocalDate.of(2026,10,4).plusDays(arrivalDay)).paymentMethod("CASH").receiptReference(run+suffix+"-paid").build();}
    void receive(long booking,String amount){as("STAFF");deposits.receive(booking,DepositRequests.Receive.builder().amount(new BigDecimal(amount))
            .paymentMethod("CASH").referenceNo(run+"receipt").requestKey(run+"receipt").build());}

    @Test @Transactional void customerCancellationUsesBoundPolicyAndReservesOnlyRemainingCredit(){policy("0","80");long b=portal(10);receive(b,"300");
        as("CUSTOMER");deposits.requestRefund(b,RefundRequests.Create.builder().amount(new BigDecimal("50"))
                .requestKey(run+"oldrefund").reason("Earlier partial refund").build());
        var fact=lifecycle.cancel(b,id("CUSTOMER"),"CUSTOMER","Guest changed plans");
        assertEquals(0,new BigDecimal("190.00").compareTo(fact.getRefundObligation()));
        assertEquals(0,new BigDecimal("60.00").compareTo(fact.getForfeitedAmount()));
        assertEquals(4,jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,b));
        assertEquals(0,deposits.summary(b).available().signum());
        assertEquals(fact.getId(),lifecycle.cancel(b,id("CUSTOMER"),"CUSTOMER","Guest changed plans").getId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b));
    }

    @Test @Transactional void customerCannotCancelAnotherReservation(){policy("0","100");long b=portal(6);as("OTHER_CUSTOMER");
        assertThrows(BusinessException.class,()->lifecycle.cancel(b,id("OTHER_CUSTOMER"),"CUSTOMER","Not mine"));}

    @Test @Transactional void partialPortalDepositIsPolicyBaseInsteadOfFullQuote(){policy("0","80");long b=portal(10);receive(b,"100");
        as("CUSTOMER");var fact=lifecycle.cancel(b,id("CUSTOMER"),"CUSTOMER","Changed travel plans");
        assertEquals(0,new BigDecimal("80.00").compareTo(fact.getRefundObligation()));
        assertEquals(0,new BigDecimal("20.00").compareTo(fact.getForfeitedAmount()));
        assertEquals(0,deposits.summary(b).available().signum());
    }

    @Test @Transactional void hotelCancellationCreatesFullOfflineRefundObligation(){policy("0","0");as("STAFF");var created=direct.create(request("hotel01",6));
        var fact=lifecycle.cancel(created.bookingId(),id("STAFF"),"HOTEL","Hotel cannot provide the room");
        assertEquals(0,created.acceptedQuote().compareTo(fact.getRefundObligation()));assertEquals(0,fact.getForfeitedAmount().signum());
        long settlement=jdbc.queryForObject("SELECT id FROM deposit_settlement WHERE booking_id=? AND kind='REFUND'",Long.class,created.bookingId());
        as("MANAGER");var paid=lifecycle.confirmExternalRefund(created.bookingId(),settlement,id("MANAGER"),run+"actual-refund");
        assertEquals(1,paid.getStatus());assertEquals(0,deposits.summary(created.bookingId()).balance().signum());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,created.bookingId()));
    }

    @Test @Transactional void noShowForfeitsOnlyRealDepositAfterArrival(){policy("0","100");as("STAFF");var created=direct.create(request("noshow01",0));
        assertThrows(BusinessException.class,()->lifecycle.noShow(created.bookingId(),id("STAFF"),"Did not arrive"));
        clock.day(1);var fact=lifecycle.noShow(created.bookingId(),id("STAFF"),"Did not arrive");
        assertEquals(created.acceptedQuote(),fact.getForfeitedAmount());assertEquals(fact.getId(),lifecycle.noShow(created.bookingId(),id("STAFF"),"Did not arrive").getId());
        assertEquals(6,jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,created.bookingId()));
        assertEquals(0,deposits.summary(created.bookingId()).balance().signum());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,created.bookingId()));
    }

    @Test @Transactional void laterPolicyNeverRepricesExistingReservationCancellation(){
        policy("0","80");long original=portal(10);long oldPolicy=jdbc.queryForObject("SELECT reservation_policy_id FROM booking WHERE id=?",Long.class,original);
        policy("0","20");long later=portal(10);receive(original,"300");
        as("CUSTOMER");var fact=lifecycle.cancel(original,id("CUSTOMER"),"CUSTOMER","Changed travel plans");
        assertEquals(oldPolicy,fact.getPolicyId());
        assertEquals(0,new BigDecimal("240.00").compareTo(fact.getRefundObligation()));
        assertNotEquals(oldPolicy,jdbc.queryForObject("SELECT reservation_policy_id FROM booking WHERE id=?",Long.class,later));
    }

    @Test @Transactional void cancellingOneReservationPreservesAnotherFutureRoomCommitment(){
        policy("0","100");as("STAFF");var first=direct.create(request("roomA01",6));
        var laterRequest=request("roomB01",11);var second=direct.create(laterRequest);
        lifecycle.cancel(first.bookingId(),id("STAFF"),"HOTEL","Hotel changed this reservation");
        assertEquals(1,jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,second.bookingId()));
        assertEquals(2,jdbc.queryForObject("SELECT status FROM room WHERE id=?",Integer.class,room));
    }

    @Test @Transactional void arrivedReservationRejectsCancellationAndNoShow(){
        policy("0","100");as("STAFF");var request=request("arrived01",0);
        request.getBookerProfile().setDocumentType("PASSPORT");request.getBookerProfile().setNationality("CA");
        var created=direct.create(request);guests.confirm(created.bookingId(),id("STAFF"));
        bookings.checkIn(created.bookingId(),id("STAFF"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,created.bookingId()));
        assertThrows(BusinessException.class,()->lifecycle.cancel(created.bookingId(),id("STAFF"),"HOTEL","Too late"));
        clock.day(1);assertThrows(BusinessException.class,()->lifecycle.noShow(created.bookingId(),id("STAFF"),"Too late"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reservation_cancellation WHERE booking_id=?",Integer.class,created.bookingId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reservation_no_show WHERE booking_id=?",Integer.class,created.bookingId()));
    }
}
