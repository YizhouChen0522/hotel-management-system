package com.johnny.hotel.wallet;

import com.johnny.hotel.dto.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.reservation.ReservationPolicyRequests;
import com.johnny.hotel.reservation.ReservationPolicyService;
import com.johnny.hotel.group.RoomTypeCapacityGuard;
import com.johnny.hotel.payment.PaymentAttempt;
import com.johnny.hotel.payment.PaymentAttemptService;
import com.johnny.hotel.payment.PaymentRequests;
import com.johnny.hotel.payment.PaymentWebhookService;
import com.johnny.hotel.payment.RawPaymentWebhook;
import com.johnny.hotel.payment.ReservationCheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.johnny.hotel.stay.RoomConflictReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "hotel.payments.mock.webhook-secret=development-test-secret-123456")
class PortalDepositInventoryDevelopmentTest extends FinancialDevelopmentFixture {
    private static final String MOCK_SECRET = "development-test-secret-123456";
    @Autowired RoomConflictReader conflicts;
    @Autowired ReservationPolicyService policies;
    @Autowired RoomTypeCapacityGuard capacity;
    @Autowired ReservationCheckoutService checkouts;
    @Autowired PaymentAttemptService paymentAttempts;
    @Autowired PaymentWebhookService paymentWebhooks;
    Long fixturePolicyId;
    boolean preserveMockLedgerFacts;

    @BeforeEach
    void ensurePortalPolicy() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM reservation_policy WHERE status=1", Integer.class) > 0) {
            return;
        }
        as("OWNER");
        var request = ReservationPolicyRequests.Save.builder()
                .name(run + " portal policy")
                .bands(List.of(ReservationPolicyRequests.Band.builder()
                        .minLeadDays(0)
                        .refundPercent(new BigDecimal("100.00"))
                        .build()))
                .build();
        fixturePolicyId = policies.activate(policies.create(request).policy().getId()).policy().getId();
    }

    @AfterEach
    void disableFixturePolicy() {
        if (fixturePolicyId != null) {
            jdbc.update("UPDATE reservation_policy SET status=2 WHERE id=?", fixturePolicyId);
        }
        if (preserveMockLedgerFacts) {
            // Provider success creates append-only financial facts. Keep their
            // complete source graph instead of deleting immutable history.
            bookingIds.clear();
            created.clear();
            room1 = 0;
            room2 = 0;
            room3 = 0;
            room4 = 0;
            type1 = 0;
            type2 = 0;
        }
    }

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

    @Test void pendingDoesNotConsumeCapacityAndConcurrentLastSlotApprovalHasOneWinner() throws Exception {
        int initial = capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket();
        assertEquals(2, initial);

        long first = create("CUSTOMER", type1, arrival, arrival.plusDays(3), "capacity-a");
        long second = create("OTHER_CUSTOMER", type1, arrival, arrival.plusDays(3), "capacity-b");
        long third = create("CUSTOMER", type1, arrival, arrival.plusDays(3), "capacity-c");
        assertEquals(initial, capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket());

        approve(first, room1);
        assertEquals(1, capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket());

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var left = pool.submit(() -> approveAfterStart(second, room2, start));
            var right = pool.submit(() -> approveAfterStart(third, room2, start));
            start.countDown();
            int winners = (left.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (right.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM booking WHERE room_type_id=? AND status=1 AND check_in_date<? AND check_out_date>?",
                Integer.class,
                type1,
                arrival.plusDays(3),
                arrival
        ));
    }

    @Test void decliningPendingBookingDoesNotConsumeInventory() {
        int before = capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket();
        long booking = create("CUSTOMER", type1, arrival, arrival.plusDays(3), "decline");
        assertEquals(before, capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket());

        bookings.rejectBooking(booking, uid("MANAGER"));

        assertEquals(before, capacity.lockAndRead(type1, arrival, arrival.plusDays(3)).availableForMarket());
        assertEquals(5, bookingState(booking));
    }

    @Test void signedMockWebhookFulfillsPortalBookingExactlyOnce() throws Exception {
        String checkoutKey = run + "mock-checkout";
        String attemptKey = run + "mock-attempt";
        String eventId = run + "mock-event";
        String providerPaymentId = run + "mock-provider";

        var checkoutRequest = new PaymentRequests.Checkout();
        checkoutRequest.setRoomTypeId(type1);
        checkoutRequest.setGuestCount(1);
        checkoutRequest.setCheckInDate(arrival);
        checkoutRequest.setCheckOutDate(arrival.plusDays(2));
        checkoutRequest.setRequestKey(checkoutKey);
        var checkout = checkouts.create(checkoutRequest, uid("CUSTOMER"));
        assertNotNull(checkout.getReservationPolicyId());

        var attemptRequest = new PaymentRequests.Attempt();
        attemptRequest.setProvider("MOCK");
        attemptRequest.setRequestKey(attemptKey);
        PaymentAttempt attempt = paymentAttempts.create(checkout.getId(), attemptRequest, uid("CUSTOMER"));
        assertEquals("PENDING", attempt.getStatus());

        var webhook = mockWebhook(attempt, eventId, providerPaymentId);
        assertThrows(BusinessException.class, () -> paymentWebhooks.receive(
                "MOCK",
                new RawPaymentWebhook(Map.of("X-Payment-Signature", List.of("invalid")), webhook.body())
        ));

        paymentWebhooks.receive("MOCK", webhook);
        paymentWebhooks.receive("MOCK", webhook);

        var completed = paymentAttempts.get(attempt.getId(), uid("CUSTOMER"));
        assertEquals("SUCCEEDED", completed.getStatus());
        assertEquals("COMPLETED", completed.getFulfillmentStatus());
        assertNotNull(completed.getBookingId());
        preserveMockLedgerFacts = true;
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM deposit_payment WHERE reference_no=?",
                Integer.class,
                String.valueOf(attempt.getId())
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM hotel_transaction_record WHERE provider='MOCK' AND provider_transaction_id=?",
                Integer.class,
                providerPaymentId
        ));

    }

    private boolean approveAfterStart(long booking, long room, CountDownLatch start) {
        try {
            start.await();
            approve(booking, room);
            return true;
        } catch (BusinessException | DataAccessException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private RawPaymentWebhook mockWebhook(
            PaymentAttempt attempt,
            String eventId,
            String providerPaymentId
    ) throws Exception {
        String body = "{\"providerEventId\":\"" + eventId
                + "\",\"eventType\":\"payment.updated\",\"merchantPaymentNo\":\""
                + attempt.getMerchantPaymentNo()
                + "\",\"providerPaymentId\":\"" + providerPaymentId
                + "\",\"status\":\"SUCCEEDED\",\"amount\":" + attempt.getAmount().toPlainString()
                + ",\"currency\":\"" + attempt.getCurrency() + "\"}";
        String canonical = String.join(
                "|",
                eventId,
                "payment.updated",
                attempt.getMerchantPaymentNo(),
                providerPaymentId,
                "SUCCEEDED",
                attempt.getAmount().toPlainString(),
                attempt.getCurrency()
        );
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(MOCK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        return new RawPaymentWebhook(
                Map.of("X-Payment-Signature", List.of(signature)),
                body.getBytes(StandardCharsets.UTF_8)
        );
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
