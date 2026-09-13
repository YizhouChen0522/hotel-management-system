package com.johnny.hotel.wallet;

import com.johnny.hotel.dto.RecordPaymentRequest;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class FolioAuthorizationDevelopmentTest extends FinancialDevelopmentFixture {
    RequestPostProcessor customer(long id) {
        var auth=new UsernamePasswordAuthenticationToken("customer",null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        auth.setDetails(id);return authentication(auth);
    }
    String paymentBody(){return "{\"amount\":10,\"paymentMethod\":\"CASH\",\"idempotencyKey\":\""+UUID.randomUUID()+"\"}";}
    String expenseBody(){return "{\"idempotencyKey\":\""+UUID.randomUUID()+"\",\"itemType\":\"SERVICE_CHARGE\",\"amount\":10,\"businessDate\":\"2026-10-01\",\"description\":\"minibar\",\"reason\":\"guest order\"}";}

    @Test void customerCanReadOwnFolioAndServiceBindsJwtIdentity()throws Exception{long own=createBooking("CUSTOMER");mvc.perform(get("/api/bookings/{id}/folio",own).with(customer(uid("CUSTOMER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(own));as("CUSTOMER");assertEquals(own,queries.byBookingForCustomer(own,uid("CUSTOMER")).bookingId());assertThrows(AccessDeniedException.class,()->queries.byBookingForCustomer(own,uid("OTHER_CUSTOMER")));}
    @Test void bookingIdIdorAndMissingIdHaveSameNonDisclosureStatus()throws Exception{long other=createBooking("OTHER_CUSTOMER");mvc.perform(get("/api/bookings/{id}/folio",other).with(customer(uid("CUSTOMER")))).andExpect(status().isNotFound()).andExpect(jsonPath("$.data").isEmpty());mvc.perform(get("/api/bookings/{id}/folio",Long.MAX_VALUE).with(customer(uid("CUSTOMER")))).andExpect(status().isNotFound()).andExpect(jsonPath("$.data").isEmpty());}
    @Test void folioIdAndPaymentDataCannotBeReadThroughAdminOrOwnedView()throws Exception{long other=createBooking("OTHER_CUSTOMER");payments.recordPayment(folio(other),payRequest("10"),uid("STAFF"));mvc.perform(get("/api/admin/billing/folios/{id}",folio(other)).with(customer(uid("CUSTOMER")))).andExpect(status().isForbidden());mvc.perform(get("/api/bookings/{id}/folio",other).with(customer(uid("CUSTOMER")))).andExpect(status().isNotFound()).andExpect(jsonPath("$.data.payments").doesNotExist());}
    @Test void refundOwnershipAndMissingResourceUseSameResponse()throws Exception{long own=createBooking("CUSTOMER"),other=createBooking("OTHER_CUSTOMER");mvc.perform(get("/api/folios/{id}/refunds",folio(own)).with(customer(uid("CUSTOMER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));mvc.perform(get("/api/folios/{id}/refunds",folio(other)).with(customer(uid("CUSTOMER")))).andExpect(status().isNotFound()).andExpect(jsonPath("$.data").isEmpty());mvc.perform(get("/api/folios/{id}/refunds",Long.MAX_VALUE).with(customer(uid("CUSTOMER")))).andExpect(status().isNotFound()).andExpect(jsonPath("$.data").isEmpty());}
    @Test void customerCannotCreateRefundForAnotherCustomer()throws Exception{long other=createBooking("OTHER_CUSTOMER");payments.recordPayment(folio(other),payRequest("10"),uid("STAFF"));String body="{\"amount\":10,\"requestKey\":\"refund_x1\",\"reason\":\"credit\"}";mvc.perform(post("/api/folios/{id}/refunds",folio(other)).with(customer(uid("CUSTOMER"))).contentType("application/json").content(body)).andExpect(status().isNotFound());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refund WHERE folio_id=?",Integer.class,folio(other)));}
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","HR_ADMIN"}) void nonOperationalRolesCannotUsePaymentExpenseOrAdminFolio(String role)throws Exception{long b=stay();mvc.perform(get("/api/admin/billing/bookings/{id}/folio",b).with(authentication(auth(role)))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/billing/folios/{id}",folio(b)).with(authentication(auth(role)))).andExpect(status().isForbidden());mvc.perform(post("/api/admin/billing/folios/{id}/payments",folio(b)).with(authentication(auth(role))).contentType("application/json").content(paymentBody())).andExpect(status().isForbidden());mvc.perform(post("/api/admin/billing/folios/{id}/expenses",folio(b)).with(authentication(auth(role))).contentType("application/json").content(expenseBody())).andExpect(status().isForbidden());}
    @Test void hrAndCustomerCannotProcessRefundRegardlessOfGuessedId()throws Exception{long b=createBooking("CUSTOMER");String body="{\"requestKey\":\"process_x1\",\"reason\":\"review\"}";for(String role:List.of("CUSTOMER","HR_ADMIN"))mvc.perform(post("/api/folios/{folio}/refunds/{refund}/confirm",folio(b),Long.MAX_VALUE).with(authentication(auth(role))).contentType("application/json").content(body)).andExpect(status().isForbidden());}
    @ParameterizedTest @ValueSource(strings={"STAFF","MANAGER","OWNER","SUPER_ADMIN"}) void operatingRolesCanReadCustomerFolio(String role)throws Exception{long b=createBooking("CUSTOMER");mvc.perform(get("/api/admin/billing/bookings/{id}/folio",b).with(authentication(auth(role)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(b));mvc.perform(get("/api/admin/billing/folios/{id}",folio(b)).with(authentication(auth(role)))).andExpect(status().isOk());}
    @Test void staffCanRecordExistingBusinessPaymentButCannotApproveRefund()throws Exception{long b=createBooking("CUSTOMER");mvc.perform(post("/api/admin/billing/folios/{id}/payments",folio(b)).with(authentication(auth("STAFF"))).contentType("application/json").content(paymentBody())).andExpect(status().isOk());String body="{\"requestKey\":\"process_x1\",\"reason\":\"review\"}";mvc.perform(post("/api/folios/{folio}/refunds/{refund}/confirm",folio(b),Long.MAX_VALUE).with(authentication(auth("STAFF"))).contentType("application/json").content(body)).andExpect(status().isForbidden());}
    @Test void paymentServiceRejectsHrCustomerAndInactiveOperator(){long b=createBooking("CUSTOMER");for(String role:List.of("HR_ADMIN","CUSTOMER"))assertThrows(AccessDeniedException.class,()->payments.recordPayment(folio(b),payRequest("10"),uid(role)));jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",uid("STAFF"));assertThrows(AccessDeniedException.class,()->payments.recordPayment(folio(b),payRequest("10"),uid("STAFF")));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE folio_id=?",Integer.class,folio(b)));}
    @Test void inactiveCustomerTokenCannotReadOwnFolio()throws Exception{long b=createBooking("CUSTOMER");jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",uid("CUSTOMER"));mvc.perform(get("/api/bookings/{id}/folio",b).with(customer(uid("CUSTOMER")))).andExpect(status().isForbidden());}
    @Test void inactiveCustomerTokenCannotReadRefunds()throws Exception{long b=createBooking("CUSTOMER");jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",uid("CUSTOMER"));mvc.perform(get("/api/folios/{id}/refunds",folio(b)).with(customer(uid("CUSTOMER")))).andExpect(status().isForbidden());}
    @Test void unauthenticatedBillingEndpointsReturn401()throws Exception{long b=createBooking("CUSTOMER");mvc.perform(get("/api/bookings/{id}/folio",b).with(anonymous())).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/billing/folios/{id}",folio(b)).with(anonymous())).andExpect(status().isUnauthorized());mvc.perform(get("/api/folios/{id}/refunds",folio(b)).with(anonymous())).andExpect(status().isUnauthorized());}
}
