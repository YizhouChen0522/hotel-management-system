package com.johnny.hotel.wallet;
import com.johnny.hotel.dto.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import com.johnny.hotel.support.IsolatedMysqlTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationalSecurityTest extends FinancialDevelopmentFixture {
    @Autowired com.johnny.hotel.booking.deposit.DepositService deposits;
    long create(){return createBooking("CUSTOMER");}
    void approve(long b){var r=new ApproveBookingRequest();r.setAssignedRoomId(room1);bookings.approveBooking(b,r,uid("MANAGER"));register(b);}
    long checkIn(){return stay();}
    int state(long b){return bookingState(b);}
    void change(long b,long room){var r=new ChangeRoomDuringStayRequest();r.setNewRoomId(room);r.setReason("test move");bookings.changeRoomDuringStay(b,r,uid("MANAGER"));}
    FolioItemCommand fee(String key,String amount){return FolioItemCommand.builder().itemType("SERVICE_CHARGE").description("test service").businessDate(arrival).quantity(BigDecimal.ONE).unitPrice(new BigDecimal(amount)).amount(new BigDecimal(amount)).eventKey(key).build();}
    @Override void pay(long b,String amount){if(jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b)==0){as("STAFF");deposits.receive(b,com.johnny.hotel.booking.deposit.DepositRequests.Receive.builder().amount(new BigDecimal(amount)).paymentMethod("CASH").requestKey(java.util.UUID.randomUUID().toString()).build());}else super.pay(b,amount);}
    void invariants(long b){if(jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE booking_id=?",Integer.class,b)==0){assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio f JOIN stay s ON s.id=f.stay_id WHERE s.booking_id=?",Integer.class,b));return;} if(!jdbc.queryForObject("SELECT user_id FROM booking WHERE id=?",Long.class,b).equals(uid("CUSTOMER")))return;invariantBooking(b);}


    @Autowired MockMvc mvc;
    RequestPostProcessor role(String role,long id){var auth=new UsernamePasswordAuthenticationToken("test",null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));auth.setDetails(uid(role.equals("CUSTOMER") && id==3?"OTHER_CUSTOMER":role));return authentication(auth);}
    @ParameterizedTest @ValueSource(strings={"STAFF","MANAGER","OWNER","SUPER_ADMIN"}) void operationalRolesCanReadAndWriteLifecycle(String role) throws Exception {
        long b=create();mvc.perform(get("/api/admin/bookings").with(role(role,2))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/admin/bookings/{id}/approve",b).with(role(role,2)).contentType("application/json").content("{\"assignedRoomId\":"+room1+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1));
        register(b);
        mvc.perform(post("/api/admin/bookings/{id}/check-in",b).with(role(role,2))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/billing/folios/{id}/payments",folio(b)).with(role(role,2)).contentType("application/json")
                .content("{\"amount\":300,\"paymentMethod\":\"CASH\",\"idempotencyKey\":\"123e4567-e89b-12d3-a456-426614174000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.folioId").value(folio(b))).andExpect(jsonPath("$.data.requestKey").doesNotExist());
        clock.day(3);mvc.perform(post("/api/admin/bookings/{id}/check-out",b).with(role(role,2))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"approve","reject","check-in","check-out","cancel"}) void hrCannotWriteBooking(String operation) throws Exception {
        long b=create();mvc.perform(post("/api/admin/bookings/{id}/"+operation,b).with(role("HR_ADMIN",2)).contentType("application/json").content("{\"assignedRoomId\":"+room1+"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"maintenance","available","booked","occupied","enable","disable"}) void hrCannotWriteRoom(String operation) throws Exception {
        mvc.perform(post("/api/admin/rooms/1/"+operation).with(role("HR_ADMIN",2))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"/api/admin/bookings","/api/admin/rooms","/api/admin/room-types"}) void hrCannotReadOperations(String uri) throws Exception {
        mvc.perform(get(uri).with(role("HR_ADMIN",2))).andExpect(status().isForbidden());
    }
    @Test void hrCannotAccessBillingOrRoomTypeWrites() throws Exception {
        long b=checkIn();mvc.perform(get("/api/admin/billing/folios/{id}",folio(b)).with(role("HR_ADMIN",2))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/billing/folios/{id}/payments",folio(b)).with(role("HR_ADMIN",2)).contentType("application/json").content("{\"amount\":10,\"paymentMethod\":\"CASH\",\"idempotencyKey\":\"123e4567-e89b-12d3-a456-426614174000\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/room-types/1/disable").with(role("HR_ADMIN",2))).andExpect(status().isForbidden());
    }
    @Test void customerOnlyOwnBookingAndFolio() throws Exception {
        long b=checkIn();mvc.perform(get("/api/bookings/{id}/folio",b).with(role("CUSTOMER",1))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(b))
                .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.totalAmount").value(300));
        mvc.perform(get("/api/bookings/{id}/folio",b).with(role("CUSTOMER",3))).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/{id}",b).with(role("CUSTOMER",3))).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/bookings/{id}/cancel",b).with(role("CUSTOMER",3))).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/billing/folios/{id}",folio(b)).with(role("CUSTOMER",1))).andExpect(status().isForbidden());invariants(b);
    }
    @Test void validAndMalformedRequestsReturnHttp400WithResult() throws Exception {
        mvc.perform(post("/api/bookings").with(role("CUSTOMER",1)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/bookings").with(role("CUSTOMER",1)).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
        long b=checkIn();mvc.perform(post("/api/admin/billing/folios/{id}/payments",folio(b)).with(role("STAFF",2)).contentType("application/json").content("{\"amount\":0,\"paymentMethod\":\"CASH\",\"idempotencyKey\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void unauthenticatedReturns401AndBusinessFailureIsNot200() throws Exception {
        mvc.perform(get("/api/admin/bookings").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        long b=create();mvc.perform(post("/api/admin/bookings/{id}/check-out",b).with(role("STAFF",2)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void internalFailureDoesNotExposeSqlOrStack() throws Exception {
        long b=create();approve(b);gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);
        mvc.perform(post("/api/admin/bookings/{id}/check-in",b).with(role("STAFF",2))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Conflicting data or concurrent operation; retry or verify the resource")).andExpect(jsonPath("$.data").isEmpty());invariants(b);
    }
}
