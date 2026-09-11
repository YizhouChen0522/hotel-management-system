package com.johnny.hotel.support;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=com.johnny.hotel.HotelBackendApplication.class, properties={"logging.level.org.springframework=WARN", "logging.level.com.johnny=WARN", "logging.level.org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration=ERROR", "debug=false"})
@AutoConfigureMockMvc
@Import(IsolatedMysqlTest.Config.class)
@EnabledIfSystemProperty(named="hotel.mysql.tests", matches="true")
public abstract class IsolatedMysqlTest {
    @TestConfiguration public static class Config {
        @Bean @Primary MutableHotelClock testClock() { return new MutableHotelClock(); }
        @Bean SqlGate sqlGate() { return new SqlGate(); }
    }
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected BookingService bookings;
    @Autowired protected RoomService rooms;
    @Autowired protected FolioService folios;
    @Autowired protected PaymentService payments;
    @Autowired protected FolioFinancialService financial;
    @Autowired protected FolioQueryService queries;
    @Autowired protected MutableHotelClock clock;
    @Autowired protected SqlGate gate;
    @Autowired protected PlatformTransactionManager txManager;
    protected final LocalDate arrival=LocalDate.of(2026,10,1);
    @BeforeEach void resetIsolatedDatabase() {
        assertEquals("hotel_lifecycle_test", jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(33079, jdbc.queryForObject("SELECT @@port",Integer.class));
        gate.clear(); clock.day(0);
        for (String table : new String[]{"wallet_transaction","wallet_top_up","wallet","expense_registration","room_billing_event","payment","stay_history","folio_item","folio","booking_room_assignment","booking_nightly_rate","booking_price_version","booking","room_rate","room","room_type","sys_audit_log","sys_user_role","sys_user"})
            jdbc.update("DELETE FROM " + table + (table.equals("folio_item") || table.equals("expense_registration") ? " ORDER BY id DESC" : ""));
        jdbc.update("INSERT INTO sys_user(id,username,password,status) VALUES(1,'test_customer','test-only',1),(2,'test_staff','test-only',1),(3,'test_other','test-only',1)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT 1,id FROM sys_role WHERE role_code='CUSTOMER'");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT 2,id FROM sys_role WHERE role_code='MANAGER'");
        jdbc.update("INSERT INTO room_type(id,type_name,base_price,capacity) VALUES(1,'test_standard',100,4),(2,'test_deluxe',150,4)");
        jdbc.update("INSERT INTO room(id,room_number,room_type_id,floor,status) VALUES(1,'T101',1,1,1),(2,'T102',1,1,1),(3,'T201',2,2,1),(4,'T202',2,2,1)");
    }
    protected long create() {
        var request=new CreateBookingRequest();request.setRoomTypeId(1L);request.setGuestCount(2);
        request.setCheckInDate(arrival);request.setCheckOutDate(arrival.plusDays(3));
        return bookings.createBooking(request,1L).getId();
    }
    protected void approve(long id) { var r=new ApproveBookingRequest();r.setAssignedRoomId(1L);bookings.approveBooking(id,r,2L); }
    protected long checkIn() { long id=create();approve(id);bookings.checkIn(id,2L);return id; }
    protected long folio(long booking) { return jdbc.queryForObject("SELECT id FROM folio WHERE booking_id=?",Long.class,booking); }
    protected RecordPaymentRequest request(String amount) { return RecordPaymentRequest.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").idempotencyKey(UUID.randomUUID().toString()).build(); }
    protected Payment pay(long booking, String amount) { return payments.recordPayment(folio(booking),request(amount),2L); }
    protected void change(long id,long room) { var r=new ChangeRoomDuringStayRequest();r.setNewRoomId(room);r.setReason("test move");bookings.changeRoomDuringStay(id,r,2L); }
    protected FolioItemCommand fee(String key, String amount) { return FolioItemCommand.builder().itemType("SERVICE_CHARGE").description("test service")
            .businessDate(arrival).quantity(BigDecimal.ONE).unitPrice(new BigDecimal(amount)).amount(new BigDecimal(amount)).eventKey(key).build(); }
    protected int state(long id) { return jdbc.queryForObject("SELECT status FROM booking WHERE id=?",Integer.class,id); }
    protected void invariants(long booking) {
        // Fresh transaction / connection after all workers committed. Never assertions on their old snapshots.
        var tx=new TransactionTemplate(txManager);tx.setPropagationBehavior(3);
        tx.executeWithoutResult(status -> {
            var f=jdbc.queryForMap("SELECT * FROM folio WHERE booking_id=?",booking);long id=((Number)f.get("id")).longValue();
            BigDecimal total=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM folio_item WHERE folio_id=?",BigDecimal.class,id);
            BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment WHERE folio_id=? AND status='SUCCESS'",BigDecimal.class,id);
            assertEquals(0,total.compareTo((BigDecimal)f.get("total_amount")));
            assertEquals(0,paid.compareTo((BigDecimal)f.get("paid_amount")));
            assertEquals(0,total.subtract(paid).compareTo((BigDecimal)f.get("balance_amount")));
            int s=state(booking);
            int active=jdbc.queryForObject("SELECT COUNT(*) FROM booking_room_assignment WHERE booking_id=? AND end_time IS NULL",Integer.class,booking);
            assertEquals(s==2?1:0,active);
            if(s==2) assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking b JOIN booking_room_assignment a ON a.booking_id=b.id AND a.end_time IS NULL JOIN room r ON r.id=a.room_id WHERE b.id=? AND b.assigned_room_id=r.id AND r.status=4 AND r.room_type_id=a.room_type_id",Integer.class,booking));
            if(s==3) { assertNotNull(f.get("closed_time"));assertEquals(3,jdbc.queryForObject("SELECT r.status FROM booking b JOIN room r ON r.id=b.assigned_room_id WHERE b.id=?",Integer.class,booking)); }
        });
    }
}
