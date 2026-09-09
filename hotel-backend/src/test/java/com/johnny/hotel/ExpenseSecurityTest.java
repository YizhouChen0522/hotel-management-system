package com.johnny.hotel;
import com.johnny.hotel.support.IsolatedMysqlTest;
import com.johnny.hotel.service.ExpenseService;
import com.johnny.hotel.dto.RegisterExpenseRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests",matches="true")
class ExpenseSecurityTest extends IsolatedMysqlTest {
    @Autowired MockMvc mvc;@Autowired ExpenseService service;
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    RequestPostProcessor role(String role){ExpenseIntegrationTest.actor(role);var auth=SecurityContextHolder.getContext().getAuthentication();SecurityContextHolder.clearContext();return authentication(auth);}
    String body(String type,int amount,Long source){return "{\"idempotencyKey\":\""+UUID.randomUUID()+"\",\"itemType\":\""+type+"\",\"amount\":"+amount+",\"businessDate\":\"2026-10-01\",\"description\":\"minibar\",\"reason\":\"guest order\""+(source==null?"":",\"sourceExpenseId\":"+source)+"}";}
    @ParameterizedTest @ValueSource(strings={"STAFF","MANAGER","OWNER","SUPER_ADMIN"}) void operatingRolesRegisterAndConfirmPositiveFees(String role) throws Exception {
        long b=checkIn();mvc.perform(post("/api/admin/billing/folios/{id}/expenses",folio(b)).with(role(role)).contentType("application/json").content(body("SERVICE_CHARGE",10,null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value("PENDING"));
        long id=queries.byBooking(b,1L).expenses().get(0).id();mvc.perform(post("/api/admin/billing/folios/{f}/expenses/{e}/confirm",folio(b),id).with(role(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));invariants(b);}
    @ParameterizedTest @ValueSource(strings={"HR_ADMIN","CUSTOMER"}) void nonOperatorsCannotReadOrMutateOperationalExpenses(String role) throws Exception {
        long b=checkIn();mvc.perform(post("/api/admin/billing/folios/{id}/expenses",folio(b)).with(role(role)).contentType("application/json").content(body("DAMAGE_CHARGE",10,null))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/billing/folios/{id}/expenses",folio(b)).with(role(role))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/billing/folios/{f}/expenses/1/confirm",folio(b)).with(role(role))).andExpect(status().isForbidden());}
    @ParameterizedTest @ValueSource(strings={"DISCOUNT","FEE_REVERSAL"}) void staffDeniedBothCreditRegistrationAndConfirmation(String type)throws Exception {
        long b=checkIn();ExpenseIntegrationTest.actor("MANAGER");var positive=service.register(folio(b),RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType("SERVICE_CHARGE").amount(new BigDecimal("10")).businessDate(arrival).description("test").reason("test").build());service.confirm(folio(b),positive.id());SecurityContextHolder.clearContext();
        mvc.perform(post("/api/admin/billing/folios/{f}/expenses",folio(b)).with(role("STAFF")).contentType("application/json").content(body(type,-1,positive.id()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/billing/folios/{f}/expenses",folio(b)).with(role("MANAGER")).contentType("application/json").content(body(type,-1,positive.id()))).andExpect(status().isOk());
        long credit=queries.byBooking(b,1L).expenses().get(1).id();mvc.perform(post("/api/admin/billing/folios/{f}/expenses/{e}/confirm",folio(b),credit).with(role("STAFF"))).andExpect(status().isForbidden());}
    @Test void validAndTypeValidationReturn400() throws Exception {long b=checkIn();mvc.perform(post("/api/admin/billing/folios/{f}/expenses",folio(b)).with(role("STAFF")).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));mvc.perform(post("/api/admin/billing/folios/{f}/expenses",folio(b)).with(role("STAFF")).contentType("application/json").content(body("REFUND",10,null))).andExpect(status().isBadRequest());}
    @Test void customerCanOnlyReadOwnExpenseStatesThroughFolio()throws Exception {long b=checkIn();ExpenseIntegrationTest.actor("MANAGER");service.register(folio(b),RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType("SERVICE_CHARGE").amount(BigDecimal.TEN).businessDate(arrival).description("test").reason("test").build());
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test",null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));auth.setDetails(1L);SecurityContextHolder.clearContext();
        mvc.perform(get("/api/bookings/{b}/folio",b).with(authentication(auth))).andExpect(status().isOk()).andExpect(jsonPath("$.data.expenses[0].status").value("PENDING")).andExpect(jsonPath("$.data.expenses[0].requestKey").doesNotExist());
        auth.setDetails(3L);mvc.perform(get("/api/bookings/{b}/folio",b).with(authentication(auth))).andExpect(status().isForbidden());}
}
