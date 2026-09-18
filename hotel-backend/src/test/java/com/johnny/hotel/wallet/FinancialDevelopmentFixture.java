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
    @Autowired com.johnny.hotel.guest.GuestService guests;
    @Autowired private RoomTurnoverTaskService fixtureTurnovers;
    @Autowired private com.johnny.hotel.inspection.InspectionService fixtureInspections;
    void completeTurnoverAndInspect(long room){
        as("MANAGER");
        long turnover=jdbc.queryForObject("SELECT id FROM room_turnover_task WHERE room_id=? ORDER BY id DESC LIMIT 1",Long.class,room);
        fixtureTurnovers.complete(turnover,"Test fixture cleaning completed");
        long inspection=jdbc.queryForObject("SELECT id FROM housekeeping_inspection WHERE turnover_task_id=? ORDER BY id DESC LIMIT 1",Long.class,turnover);
        fixtureInspections.decide(inspection,com.johnny.hotel.inspection.InspectionRequests.Decide.builder().passed(true).build());
    }
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
        // Include an idempotent request that committed even when the caller's
        // response path failed, so the fixture never leaks reservation rows.
        bookingIds.addAll(jdbc.queryForList("SELECT id FROM booking WHERE walk_in_request_key LIKE ?",Long.class,run+"%"));
        jdbc.update("DELETE FROM damage_assessment WHERE room_id IN (?,?,?,?)",room1,room2,room3,room4);
        jdbc.update("DELETE FROM room_work_order WHERE room_id IN (?,?,?,?)",room1,room2,room3,room4);
        for(long user:created)jdbc.update("DELETE FROM wallet_transaction WHERE wallet_id IN (SELECT id FROM wallet WHERE user_id=?)",user);
        for(long booking:bookingIds){
            var guestIds=jdbc.queryForList("SELECT guest_id FROM booking_guest WHERE booking_id=?",Long.class,booking);
            var bookerIds=jdbc.queryForList("SELECT booker_guest_profile_id FROM booking WHERE id=? AND booker_guest_profile_id IS NOT NULL",Long.class,booking);
            var stayIds=jdbc.queryForList("SELECT id FROM stay WHERE booking_id=?",Long.class,booking);
            jdbc.update("DELETE FROM deposit_transfer WHERE account_id IN (SELECT id FROM reservation_deposit_account WHERE booking_id=?)",booking);
            jdbc.update("DELETE FROM deposit_refund WHERE account_id IN (SELECT id FROM reservation_deposit_account WHERE booking_id=?)",booking);
            jdbc.update("DELETE FROM deposit_payment WHERE account_id IN (SELECT id FROM reservation_deposit_account WHERE booking_id=?)",booking);
            jdbc.update("DELETE FROM reservation_deposit_account WHERE booking_id=?",booking);
            for(long stay:stayIds){
                var tasks=new HashSet<Long>();
                tasks.addAll(jdbc.queryForList("SELECT task_id FROM guest_service_order WHERE stay_id=?",Long.class,stay));
                tasks.addAll(jdbc.queryForList("SELECT followup_task_id FROM housekeeping_inspection WHERE stay_id=? AND followup_task_id IS NOT NULL",Long.class,stay));
                tasks.addAll(jdbc.queryForList("SELECT task_id FROM room_turnover_task WHERE stay_id=?",Long.class,stay));
                tasks.addAll(jdbc.queryForList("SELECT task_id FROM stayover_cleaning_request WHERE stay_id=?",Long.class,stay));
                jdbc.update("DELETE FROM guest_service_order WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM guest_purchase_item WHERE purchase_id IN (SELECT id FROM guest_purchase WHERE stay_id=?)",stay);
                jdbc.update("DELETE FROM guest_purchase WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE booking_id=?)",booking);
                jdbc.update("DELETE FROM invoice WHERE booking_id=?",booking);
                jdbc.update("DELETE FROM rework_cleaning_request WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM housekeeping_inspection WHERE stay_id=? AND repair_order_id IS NOT NULL",stay);
                jdbc.update("DELETE FROM room_repair_task WHERE inspection_id IN (SELECT id FROM housekeeping_inspection WHERE stay_id=?)",stay);
                jdbc.update("DELETE FROM housekeeping_inspection WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM cleaning_record WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM stayover_cleaning_request WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM room_turnover_task WHERE stay_id=?",stay);
                for(long task:tasks){jdbc.update("DELETE FROM task_record WHERE task_id=?",task);jdbc.update("DELETE FROM todo WHERE task_id=?",task);jdbc.update("DELETE FROM task_assignment WHERE task_id=?",task);jdbc.update("DELETE FROM hotel_task WHERE id=?",task);}
                for(long folio:jdbc.queryForList("SELECT id FROM folio WHERE stay_id=?",Long.class,stay)){
                    jdbc.update("DELETE FROM refund WHERE folio_id=?",folio);
                    jdbc.update("DELETE FROM expense_registration WHERE folio_id=? ORDER BY id DESC",folio);
                    jdbc.update("DELETE FROM payment WHERE folio_id=?",folio);
                    jdbc.update("DELETE FROM stay_extension_nightly_rate WHERE stay_id=?",stay);
                    jdbc.update("DELETE FROM folio_item WHERE folio_id=? ORDER BY id DESC",folio);
                    jdbc.update("DELETE FROM stay_adjustment WHERE stay_id=? ORDER BY id DESC",stay);
                    jdbc.update("DELETE FROM stay_history WHERE folio_id=?",folio);
                    jdbc.update("DELETE FROM folio WHERE id=?",folio);
                }
                jdbc.update("DELETE FROM room_billing_event WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM stay_room_assignment WHERE stay_id=?",stay);
                guestIds.addAll(jdbc.queryForList("SELECT guest_id FROM stay_guest WHERE stay_id=?",Long.class,stay));
                jdbc.update("DELETE FROM stay_guest WHERE stay_id=?",stay);
                jdbc.update("DELETE FROM stay WHERE id=?",stay);
            }
            jdbc.update("DELETE FROM guest_registration WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking_guest WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking_nightly_rate WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking_price_version WHERE booking_id=?",booking);
            jdbc.update("DELETE FROM booking WHERE id=?",booking);
            for(long guest:guestIds)jdbc.update("DELETE FROM guest_profile WHERE id=? AND linked_user_id IS NULL",guest);
            for(long guest:bookerIds)jdbc.update("DELETE FROM guest_profile WHERE id=? AND linked_user_id IS NULL",guest);
        }
        jdbc.update("DELETE FROM walk_in_request_lock WHERE request_key LIKE ?",run+"%");
        jdbc.update("DELETE FROM room WHERE id IN (?,?,?,?)",room1,room2,room3,room4);
        jdbc.update("DELETE FROM room_rate WHERE room_type_id IN (?,?)",type1,type2);
        jdbc.update("DELETE FROM room_type WHERE id IN (?,?)",type1,type2);
        jdbc.update("DELETE FROM guest_profile WHERE linked_user_id IS NULL AND document_number LIKE ? AND NOT EXISTS (SELECT 1 FROM booking_guest bg WHERE bg.guest_id=guest_profile.id)","P"+run+"%");
        jdbc.update("DELETE FROM charge_catalog WHERE code LIKE ?",("EX"+run+"%").toUpperCase(java.util.Locale.ROOT));
    }
    long createBooking(String role){var r=new CreateBookingRequest();r.setRoomTypeId(type1);r.setGuestCount(2);r.setCheckInDate(arrival);r.setCheckOutDate(arrival.plusDays(3));long id=bookings.createBooking(r,uid(role)).getId();bookingIds.add(id);return id;}
    void register(long id){var p=com.johnny.hotel.guest.GuestRequests.Profile.builder().firstName("Test").lastName("Guest").nationality("CA").documentType("PASSPORT").documentNumber(run+"-"+id).build();guests.addToBooking(id,com.johnny.hotel.guest.GuestRequests.Add.builder().role(com.johnny.hotel.guest.GuestRole.PRIMARY).profile(p).build(),uid("MANAGER"));guests.confirm(id,uid("MANAGER"));}
    long stay(){return stay("CUSTOMER");}
    long stay(String role){long id=createBooking(role);var r=new ApproveBookingRequest();r.setAssignedRoomId(role.equals("CUSTOMER")?room1:room2);bookings.approveBooking(id,r,uid("MANAGER"));register(id);bookings.checkIn(id,uid("MANAGER"));return id;}
    long sid(long booking){return jdbc.queryForObject("SELECT id FROM stay WHERE booking_id=?",Long.class,booking);}
    long folio(long b){return jdbc.queryForObject("SELECT f.id FROM folio f JOIN stay s ON s.id=f.stay_id WHERE s.booking_id=?",Long.class,b);}
    RecordPaymentRequest payRequest(String amount){return RecordPaymentRequest.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").idempotencyKey(UUID.randomUUID().toString()).build();}
    void pay(long b,String amount){payments.recordPayment(folio(b),payRequest(amount),uid("STAFF"));}
    void checkout(long b){bookings.checkOut(b,uid("MANAGER"));}
    int stayState(long b){assertEquals(1,bookingState(b));return jdbc.queryForObject("SELECT status FROM stay WHERE booking_id=?",Integer.class,b);}
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
        assertEquals(1,bookingState(b));
        int status=jdbc.queryForObject("SELECT status FROM stay WHERE id=?",Integer.class,sid(b));
        assertEquals(status==1?1:0,jdbc.queryForObject("SELECT COUNT(*) FROM stay_room_assignment WHERE stay_id=? AND end_time IS NULL",Integer.class,sid(b)));
        if(status==2){assertNotNull(view.closedTime());assertEquals(0,view.balanceAmount().signum());assertEquals(3,jdbc.queryForObject("SELECT r.status FROM room r JOIN stay_room_assignment a ON a.room_id=r.id WHERE a.stay_id=(SELECT id FROM stay WHERE booking_id=?) ORDER BY a.id DESC LIMIT 1",Integer.class,b));}
        invariant(wid("CUSTOMER"));
    }
    RegisterExpenseRequest expense(String amount){return RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType("SERVICE_CHARGE").amount(new BigDecimal(amount)).businessDate(arrival).description("test expense").reason("test").build();}
    Future<Throwable> worker(ExecutorService pool,String name,Runnable task){return pool.submit(()->{Thread.currentThread().setName(name);as("MANAGER");try{task.run();return null;}catch(Throwable e){return e;}finally{SecurityContextHolder.clearContext();}});}
    Throwable result(Future<Throwable> f){try{return f.get(20,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}}
    Throwable serialized(String statement,Runnable first,Runnable second){var pool=Executors.newFixedThreadPool(2);gate.arm("financial-first",statement,false);
        try{var a=worker(pool,"financial-first",first);SqlGate.await(gate.reached);var b=worker(pool,"financial-second",second);assertThrows(TimeoutException.class,()->b.get(150,TimeUnit.MILLISECONDS));gate.release.countDown();assertNull(result(a));return result(b);}
        finally{gate.clear();pool.shutdown();try{assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}}
}
