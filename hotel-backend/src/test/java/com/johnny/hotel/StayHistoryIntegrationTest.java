package com.johnny.hotel;

import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.dto.CreateBookingRequest;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.service.StayHistoryService;
import com.johnny.hotel.support.IsolatedMysqlTest;
import com.johnny.hotel.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfSystemProperty(named = "hotel.mysql.tests", matches = "true")
class StayHistoryIntegrationTest extends IsolatedMysqlTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired StayHistoryService stayHistory;
    @Autowired ObjectMapper mapper;

    private int historyCount(long folioId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stay_history WHERE folio_id=?", Integer.class, folioId);
    }

    private List<Map<String, Object>> historyRows(long folioId) {
        return jdbc.queryForList("SELECT * FROM stay_history WHERE folio_id=? ORDER BY actual_check_in_time ASC, id ASC", folioId);
    }

    private List<Map<String, Object>> assignments(long bookingId) {
        return jdbc.queryForList("SELECT * FROM booking_room_assignment WHERE booking_id=? ORDER BY start_time ASC, id ASC", bookingId);
    }

    private LocalDateTime time(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }

    private long longValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private String email(long userId) { return "stayuser" + userId + "@example.test"; }

    private String token(long userId) {
        return jwt.generateToken(userId, email(userId), "stayuser" + userId, List.of("CUSTOMER"));
    }

    private void setEmail(long userId) {
        jdbc.update("UPDATE sys_user SET email=? WHERE id=?", email(userId), userId);
    }

    private long createFor(long userId, long roomId, LocalDate checkIn, int nights) {
        var request = new CreateBookingRequest();
        request.setRoomTypeId(1L);
        request.setGuestCount(2);
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkIn.plusDays(nights));
        long id = bookings.createBooking(request, userId).getId();
        var approve = new ApproveBookingRequest();
        approve.setAssignedRoomId(roomId);
        bookings.approveBooking(id, approve, 2L);
        return id;
    }

    private JsonNode getData(String path, String token) throws Exception {
        MvcResult result = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, body.path("code").asInt());
        return body.path("data");
    }

    // ---------- generation: single stay, no room change ----------

    @Test
    void singleStayWithoutRoomChangeCreatesExactlyOneHistoryRow() {
        long b = checkIn();
        pay(b, "300");
        clock.day(3);
        assertEquals(0, historyCount(folio(b)));
        bookings.checkOut(b, 2L);

        var rows = historyRows(folio(b));
        assertEquals(1, rows.size());

        var assignments = assignments(b);
        assertEquals(1, assignments.size());

        Map<String, Object> row = rows.get(0);
        Map<String, Object> assignment = assignments.get(0);
        assertEquals(1L, longValue(row, "user_id"));
        assertEquals(folio(b), longValue(row, "folio_id"));
        assertEquals(longValue(assignment, "id"), longValue(row, "assignment_id"));
        assertEquals(time(assignment, "start_time"), time(row, "actual_check_in_time"));
        assertEquals(time(assignment, "end_time"), time(row, "actual_check_out_time"));
        assertEquals("T101", row.get("room_number"));
        assertEquals("test_standard", row.get("room_type_name"));
        invariants(b);
    }

    // ---------- generation: room change produces continuous segments ----------

    @Test
    void roomChangeCreatesOneHistorySegmentPerAssignmentWithContinuousTimes() {
        long b = checkIn();
        clock.day(1);
        change(b, 3);
        pay(b, "400");
        clock.day(3);
        bookings.checkOut(b, 2L);

        var assignments = assignments(b);
        assertEquals(2, assignments.size());

        var rows = historyRows(folio(b));
        assertEquals(2, rows.size());

        Map<String, Object> first = assignments.get(0);
        Map<String, Object> second = assignments.get(1);

        assertEquals("T101", rows.get(0).get("room_number"));
        assertEquals("test_standard", rows.get(0).get("room_type_name"));
        assertEquals("T201", rows.get(1).get("room_number"));
        assertEquals("test_deluxe", rows.get(1).get("room_type_name"));

        // segment 1: assignment 1 start/end
        assertEquals(longValue(first, "id"), longValue(rows.get(0), "assignment_id"));
        assertEquals(time(first, "start_time"), time(rows.get(0), "actual_check_in_time"));
        assertEquals(time(first, "end_time"), time(rows.get(0), "actual_check_out_time"));
        // segment 2: assignment 2 start/end
        assertEquals(longValue(second, "id"), longValue(rows.get(1), "assignment_id"));
        assertEquals(time(second, "start_time"), time(rows.get(1), "actual_check_in_time"));
        assertEquals(time(second, "end_time"), time(rows.get(1), "actual_check_out_time"));
        // both segments belong to the same booking/folio/user
        assertEquals(folio(b), longValue(rows.get(0), "folio_id"));
        assertEquals(folio(b), longValue(rows.get(1), "folio_id"));
        assertEquals(1L, longValue(rows.get(0), "user_id"));
        assertEquals(1L, longValue(rows.get(1), "user_id"));
        // continuity: first segment ends exactly when the second starts
        assertEquals(time(rows.get(0), "actual_check_out_time"), time(rows.get(1), "actual_check_in_time"));
        invariants(b);
    }

    // ---------- history only appears after a successful checkout ----------

    @Test
    void noHistoryWhileStayingOrWhenCheckoutIsBlockedByBalance() {
        long b = checkIn();
        clock.day(1);
        change(b, 3);
        // during the stay, even after a room change: no history
        assertEquals(0, historyCount(folio(b)));

        pay(b, "299");
        clock.day(3);
        assertThrows(BusinessException.class, () -> bookings.checkOut(b, 2L));
        assertEquals(0, historyCount(folio(b)));
        assertEquals(2, state(b));
        assertNull(queries.byBooking(b, 1L).closedTime());
        invariants(b);
    }

    // ---------- checkout failure rolls history back; retry does not duplicate ----------

    @Test
    void checkoutFailureAfterHistoryInsertRollsBackAndRetryCreatesExactlyOneRow() {
        long b = checkIn();
        pay(b, "300");
        clock.day(3);
        // fail a statement executed after stay_history insert: room status transition
        gate.arm(Thread.currentThread().getName(), "RoomMapper.transitionStatus", true);
        assertThrows(Exception.class, () -> bookings.checkOut(b, 2L));
        assertEquals(0, historyCount(folio(b)));
        assertEquals(2, state(b));
        assertNull(queries.byBooking(b, 1L).closedTime());

        gate.clear();
        bookings.checkOut(b, 2L);
        assertEquals(1, historyCount(folio(b)));
        assertEquals(3, state(b));
        invariants(b);
    }

    // ---------- idempotency and UNIQUE(assignment_id) guard ----------

    @Test
    void repeatedGenerationIsSkippedAndUniqueAssignmentConstraintRejectsDuplicates() {
        long b = checkIn();
        clock.day(1);
        change(b, 3);
        pay(b, "400");
        clock.day(3);
        bookings.checkOut(b, 2L);
        assertEquals(2, historyCount(folio(b)));

        // re-running the generator must not add anything
        stayHistory.createForCompletedStay(b);
        assertEquals(2, historyCount(folio(b)));

        // the database-level UNIQUE(assignment_id) also refuses a forged duplicate
        var row = historyRows(folio(b)).get(0);
        assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO stay_history(user_id,folio_id,assignment_id,actual_check_in_time,actual_check_out_time,room_number,room_type_name) VALUES(?,?,?,?,?,?,?)",
                longValue(row, "user_id"), longValue(row, "folio_id"), longValue(row, "assignment_id"),
                time(row, "actual_check_in_time"), time(row, "actual_check_out_time"),
                row.get("room_number"), row.get("room_type_name")));
        assertEquals(2, historyCount(folio(b)));
        invariants(b);
    }

    // ---------- HTTP: /me returns only the JWT user's records, sorted DESC ----------

    @Test
    void meEndpointReturnsOnlyOwnRecordsSortedByCheckInTimeDesc() throws Exception {
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT 3,id FROM sys_role WHERE role_code='CUSTOMER'");
        setEmail(1);
        setEmail(3);

        clock.day(0);
        long ownEarly = createFor(1, 1, arrival, 3);
        long otherUser = createFor(3, 2, arrival, 3);
        long ownLate = createFor(1, 1, arrival.plusDays(5), 3);

        bookings.checkIn(ownEarly, 2L);
        pay(ownEarly, "300");
        clock.day(3);
        bookings.checkOut(ownEarly, 2L);

        clock.day(0);
        bookings.checkIn(otherUser, 2L);
        pay(otherUser, "300");
        clock.day(3);
        bookings.checkOut(otherUser, 2L);

        rooms.setRoomAvailable(1L);
        clock.day(5);
        bookings.checkIn(ownLate, 2L);
        pay(ownLate, "300");
        clock.day(8);
        bookings.checkOut(ownLate, 2L);

        // user 1: exactly the two own folios, newest check-in first
        JsonNode mine = getData("/api/stay-history/me", token(1));
        assertEquals(2, mine.size());
        assertEquals(folio(ownLate), mine.get(0).path("folioId").asLong());
        assertEquals(folio(ownEarly), mine.get(1).path("folioId").asLong());
        assertTrue(mine.get(0).path("actualCheckInTime").asString()
                .compareTo(mine.get(1).path("actualCheckInTime").asString()) > 0);
        for (JsonNode item : mine) assertEquals(1, item.path("userId").asLong());

        // user 3: only its own single record
        JsonNode theirs = getData("/api/stay-history/me", token(3));
        assertEquals(1, theirs.size());
        assertEquals(folio(otherUser), theirs.get(0).path("folioId").asLong());
        assertEquals(3, theirs.get(0).path("userId").asLong());

        invariants(ownEarly);
        invariants(ownLate);
        invariants(otherUser);
    }

    // ---------- HTTP: folio endpoint is restricted to folioId + currentUserId ----------

    @Test
    void folioEndpointNeverLeaksAnotherCustomersHistory() throws Exception {
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT 3,id FROM sys_role WHERE role_code='CUSTOMER'");
        setEmail(1);
        setEmail(3);

        clock.day(0);
        long mine = createFor(1, 1, arrival, 3);
        long otherUser = createFor(3, 2, arrival, 3);

        bookings.checkIn(mine, 2L);
        pay(mine, "300");
        clock.day(3);
        bookings.checkOut(mine, 2L);

        clock.day(0);
        bookings.checkIn(otherUser, 2L);
        pay(otherUser, "300");
        clock.day(3);
        bookings.checkOut(otherUser, 2L);

        // knowing the other customer's folioId does not reveal anything
        JsonNode foreign = getData("/api/stay-history/me/folios/" + folio(otherUser), token(1));
        assertEquals(0, foreign.size());

        JsonNode reverse = getData("/api/stay-history/me/folios/" + folio(mine), token(3));
        assertEquals(0, reverse.size());

        // the owner still sees its own segments
        JsonNode own = getData("/api/stay-history/me/folios/" + folio(otherUser), token(3));
        assertEquals(1, own.size());
        assertEquals(3, own.get(0).path("userId").asLong());

        invariants(mine);
        invariants(otherUser);
    }

    // ---------- HTTP: segments of one folio are returned ASC ----------

    @Test
    void folioEndpointReturnsRoomChangeSegmentsAscendingWithSnapshots() throws Exception {
        long b = checkIn();
        clock.day(1);
        change(b, 3);
        pay(b, "400");
        clock.day(3);
        bookings.checkOut(b, 2L);
        setEmail(1);

        JsonNode segments = getData("/api/stay-history/me/folios/" + folio(b), token(1));
        assertEquals(2, segments.size());

        assertEquals("T101", segments.get(0).path("roomNumber").asString());
        assertEquals("test_standard", segments.get(0).path("roomTypeName").asString());
        assertEquals("T201", segments.get(1).path("roomNumber").asString());
        assertEquals("test_deluxe", segments.get(1).path("roomTypeName").asString());
        assertEquals(folio(b), segments.get(0).path("folioId").asLong());
        assertEquals(folio(b), segments.get(1).path("folioId").asLong());

        String firstIn = segments.get(0).path("actualCheckInTime").asString();
        String firstOut = segments.get(0).path("actualCheckOutTime").asString();
        String secondIn = segments.get(1).path("actualCheckInTime").asString();
        String secondOut = segments.get(1).path("actualCheckOutTime").asString();
        assertTrue(firstIn.compareTo(secondIn) < 0);
        // continuity across the room change
        assertEquals(firstOut, secondIn);
        assertTrue(secondOut.compareTo(secondIn) > 0);
        invariants(b);
    }

    // ---------- HTTP: unauthenticated requests are rejected ----------

    @Test
    void unauthenticatedOrInvalidTokenRequestsAreRejectedWith401() throws Exception {
        mvc.perform(get("/api/stay-history/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stay-history/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stay-history/me/folios/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stay-history/me/folios/1").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
