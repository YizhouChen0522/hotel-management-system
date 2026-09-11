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

@Import(FinancialDevelopmentFixture.TimeConfig.class)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
abstract class FinancialDevelopmentFixture extends WalletDevelopmentFixture {
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableHotelClock refundTestClock(){return new MutableHotelClock();}}
    @Autowired MutableHotelClock clock;
    @Autowired RefundService refunds;
    @Autowired BookingService bookings;
    @Autowired FolioService folios;
    @Autowired FolioFinancialService financial;
    @Autowired FolioQueryService queries;
    @Autowired PaymentService payments;
    @Autowired ExpenseService expenses;
    @Autowired RoomService rooms;
    final LocalDate arrival=LocalDate.of(2026,10,1);
    long type1,type2,room1,room2,room3,room4;
    final List<Long> bookingIds=new ArrayList<>();
    @BeforeEach void seedOnlyDedicatedRooms(){
        clock.day(0);
        jdbc.update("INSERT INTO room_type(type_name,base_price,capacity) VALUES(?,100,4),(?,150,4)",run+"_standard",run+"_deluxe");
        type1=jdbc.queryForObject("SELECT id FROM room_type WHERE type_name=?",Long.class,run+"_standard");type2=jdbc.queryForObject("SELECT id FROM room_type WHERE type_name=?",Long.class,run+"_deluxe");
        long[] ids=new long[4];for(int i=0;i<4;i++){jdbc.update("INSERT INTO room(room_number,room_type_id,floor,status) VALUES(?,?,1,1)",run+"_"+i,i<2?type1:type2);ids[i]=jdbc.queryForObject("SELECT id FROM room WHERE room_number=?",Long.class,run+"_"+i);}
        room1=ids[0];room2=ids[1];room3=ids[2];room4=ids[3];
    }
    @AfterEach void cleanupOnlyFinancialFixture(){
        gate.clear();
        for(long user:created)jdbc.update("DELETE FROM wallet_transaction WHERE wallet_id IN (SELECT id FROM wallet WHERE user_id=?)",user);
        for(long booking:bookingIds){
            long folio=folio(booking);
            jdbc.update("DELETE FROM room_turnover_task WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM refund WHERE folio_id=?",folio);
            jdbc.update("DELETE FROM expense_registration WHERE folio_id=? ORDER BY id DESC",folio);
            jdbc.update("DELETE FROM payment WHERE folio_id=?",folio);
            jdbc.update("DELETE FROM stay_extension_nightly_rate WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM folio_item WHERE folio_id=? ORDER BY id DESC",folio);
            jdbc.update("DELETE FROM stay_adjustment WHERE booking_id=? ORDER BY id DESC",booking);
            jdbc.update("DELETE FROM stay_history WHERE folio_id=?",folio);
            jdbc.update("DELETE FROM folio WHERE id=?",folio);
            jdbc.update("DELETE FROM room_billing_event WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking_room_assignment WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking_nightly_rate WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking_price_version WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking WHERE id=?",booking);
        }
        jdbc.update("DELETE FROM room WHERE id IN (?,?,?,?)",room1,room2,room3,room4);
        jdbc.update("DELETE FROM room_rate WHERE room_type_id IN (?,?)",type1,type2);
        jdbc.update("DELETE FROM room_type WHERE id IN (?,?)",type1,type2);
    }
    long createBooking(String role){var r=new CreateBookingRequest();r.setRoomTypeId(type1);r.setGuestCount(2);r.setCheckInDate(arrival);r.setCheckOutDate(arrival.plusDays(3));long id=bookings.createBooking(r,uid(role)).getId();bookingIds.add(id);return id;}
    long stay(){long id=createBooking("CUSTOMER");var r=new ApproveBookingRequest();r.setAssignedRoomId(room1);bookings.approveBooking(id,r,uid("MANAGER"));bookings.checkIn(id,uid("MANAGER"));return id;}
    long folio(long b){return jdbc.queryForObject("SELECT id FROM folio WHERE booking_id=?",Long.class,b);}
    RecordPaymentRequest payRequest(String amount){return RecordPaymentRequest.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").idempotencyKey(UUID.randomUUID().toString()).build();}
    void pay(long b,String amount){payments.recordPayment(folio(b),payRequest(amount),uid("STAFF"));}
    void checkout(long b){bookings.checkOut(b,uid("MANAGER"));}
    int bookingState(long b){return jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,b);}
    RefundRequests.Create refundRequest(String amount,String key){return RefundRequests.Create.builder().amount(new BigDecimal(amount)).requestKey(key).reason("Unused Folio credit").build();}
    RefundRequests.Process process(String key){return RefundRequests.Process.builder().requestKey(key).reason("Reviewed by manager").build();}
    RefundVO requestRefund(long b,String amount,String key){as("CUSTOMER");return refunds.create(folio(b),refundRequest(amount,key));}
    void creditWallet(String amount){as("CUSTOMER");var t=service.createTopUp(wid("CUSTOMER"),amount(amount,UUID.randomUUID().toString()));as("STAFF");service.confirm(t.walletId(),t.id(),decision(UUID.randomUUID().toString()));}
    void invariantBooking(long b){
        var view=queries.byBooking(b,uid("CUSTOMER"));long f=folio(b);
        var total=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM folio_item WHERE folio_id=?",BigDecimal.class,f);
        var paid=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment WHERE folio_id=? AND status='SUCCESS'",BigDecimal.class,f);
        var refunded=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM refund WHERE folio_id=? AND status=1",BigDecimal.class,f);
        assertEquals(0,total.compareTo(view.totalAmount()));assertEquals(0,paid.compareTo(view.paidAmount()));assertEquals(0,refunded.compareTo(view.refundedAmount()));assertEquals(0,total.subtract(paid).add(refunded).compareTo(view.balanceAmount()));
        assertEquals(bookingState(b)==2?1:0,jdbc.queryForObject("SELECT COUNT(*) FROM booking_room_assignment WHERE booking_id=? AND end_time IS NULL",Integer.class,b));
        if(bookingState(b)==3){assertNotNull(view.closedTime());assertEquals(0,view.balanceAmount().signum());assertEquals(3,jdbc.queryForObject("SELECT r.status FROM room r JOIN booking b ON b.assigned_room_id=r.id WHERE b.id=?",Integer.class,b));}
        invariant(wid("CUSTOMER"));
    }
    RegisterExpenseRequest expense(String amount){return RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType("SERVICE_CHARGE").amount(new BigDecimal(amount)).businessDate(arrival).description("test expense").reason("test").build();}
    Future<Throwable> worker(ExecutorService pool,String name,Runnable task){return pool.submit(()->{Thread.currentThread().setName(name);as("MANAGER");try{task.run();return null;}catch(Throwable e){return e;}finally{SecurityContextHolder.clearContext();}});}
    Throwable result(Future<Throwable> f){try{return f.get(20,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}}
    Throwable serialized(String statement,Runnable first,Runnable second){var pool=Executors.newFixedThreadPool(2);gate.arm("financial-first",statement,false);
        try{var a=worker(pool,"financial-first",first);SqlGate.await(gate.reached);var b=worker(pool,"financial-second",second);assertThrows(TimeoutException.class,()->b.get(150,TimeUnit.MILLISECONDS));gate.release.countDown();assertNull(result(a));return result(b);}
        finally{gate.clear();pool.shutdown();try{assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}}
}
