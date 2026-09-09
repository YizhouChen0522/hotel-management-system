package com.johnny.hotel;
import com.johnny.hotel.support.*;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.service.ExpenseService;
import com.johnny.hotel.vo.ExpenseVO;
import com.johnny.hotel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests",matches="true")
class ExpenseIntegrationTest extends IsolatedMysqlTest {
    @Autowired ExpenseService service;
    static void actor(String role){var auth=new UsernamePasswordAuthenticationToken("test",null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));auth.setDetails(2L);SecurityContextHolder.getContext().setAuthentication(auth);}
    @BeforeEach void manager(){actor("MANAGER");}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    RegisterExpenseRequest expense(String type,String amount,Long source){return RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType(type).amount(new BigDecimal(amount)).businessDate(arrival).description("test expense").reason("guest request / correction").sourceExpenseId(source).build();}
    ExpenseVO post(long b,String type,String amount,Long source){var e=service.register(folio(b),expense(type,amount,source));return service.confirm(folio(b),e.id());}
    CancelExpenseRequest cancellation(){return CancelExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).reason("entry error").build();}
    @Test void pendingIsNotLedgerOrPaymentAndBlocksCheckout(){long b=checkIn();var e=service.register(folio(b),expense("SERVICE_CHARGE","20",null));assertEquals("PENDING",e.status());
        var view=queries.byBooking(b,1L);assertEquals(300,view.totalAmount().intValueExact());assertEquals(0,view.paidAmount().signum());assertEquals(1,view.expenses().size());pay(b,"300");clock.day(3);
        assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);}
    @Test void fullExpenseDiscountAndCorrectionLifecycleCanCheckout(){long b=checkIn();actor("STAFF");var first=post(b,"SERVICE_CHARGE","30",null);post(b,"DAMAGE_CHARGE","20",null);
        actor("MANAGER");post(b,"DISCOUNT","-5",first.id());post(b,"FEE_REVERSAL","-25",first.id());post(b,"SERVICE_CHARGE","12",null);
        assertEquals(332,queries.byBooking(b,1L).totalAmount().intValueExact());assertEquals(300,jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,b).intValueExact());
        pay(b,"332");clock.day(3);bookings.checkOut(b,2L);invariants(b);}
    @Test void registerAndConfirmRetriesAreIdempotent(){long b=checkIn();var r=expense("SERVICE_CHARGE","10",null);var first=service.register(folio(b),r);assertEquals(first.id(),service.register(folio(b),r).id());
        var posted=service.confirm(folio(b),first.id());assertEquals(posted.ledgerItemId(),service.confirm(folio(b),first.id()).ledgerItemId());
        r.setAmount(new BigDecimal("11"));assertThrows(BusinessException.class,()->service.register(folio(b),r));assertEquals(310,queries.byBooking(b,1L).totalAmount().intValueExact());invariants(b);}
    @Test void cancelPendingIsIdempotentAndRetainsFact(){long b=checkIn();var e=service.register(folio(b),expense("DAMAGE_CHARGE","10",null));var r=cancellation();service.cancel(folio(b),e.id(),r);service.cancel(folio(b),e.id(),r);
        assertEquals("CANCELLED",queries.byBooking(b,1L).expenses().get(0).status());assertThrows(BusinessException.class,()->service.confirm(folio(b),e.id()));r.setReason("different");assertThrows(BusinessException.class,()->service.cancel(folio(b),e.id(),r));
        pay(b,"300");clock.day(3);bookings.checkOut(b,2L);invariants(b);}
    @Test void negativeRequestsReserveRemainingCreditAndCancellationReleasesIt(){long b=checkIn();var source=post(b,"SERVICE_CHARGE","10",null);var credit=service.register(folio(b),expense("DISCOUNT","-6",source.id()));
        assertThrows(BusinessException.class,()->service.register(folio(b),expense("FEE_REVERSAL","-5",source.id())));service.cancel(folio(b),credit.id(),cancellation());post(b,"FEE_REVERSAL","-10",source.id());
        assertThrows(BusinessException.class,()->service.register(folio(b),expense("DISCOUNT","-1",source.id())));invariants(b);}
    @Test void crossFolioSourceAndExpenseIdsRejected(){long b=checkIn();var e=post(b,"SERVICE_CHARGE","10",null);long other=create();var approval=new ApproveBookingRequest();approval.setAssignedRoomId(2L);bookings.approveBooking(other,approval,2L);bookings.checkIn(other,2L);
        assertThrows(BusinessException.class,()->service.register(folio(other),expense("DISCOUNT","-1",e.id())));assertThrows(BusinessException.class,()->service.confirm(folio(other),e.id()));invariants(b);invariants(other);}
    @Test void staffCannotRegisterConfirmOrCancelCredits(){long b=checkIn();var source=post(b,"SERVICE_CHARGE","10",null);var e=service.register(folio(b),expense("DISCOUNT","-1",source.id()));actor("STAFF");
        assertThrows(AccessDeniedException.class,()->service.register(folio(b),expense("DISCOUNT","-1",source.id())));assertThrows(AccessDeniedException.class,()->service.confirm(folio(b),e.id()));assertThrows(AccessDeniedException.class,()->service.cancel(folio(b),e.id(),cancellation()));}
    @Test void cannotCancelConfirmedChargeOrReverseDiscount(){long b=checkIn();var source=post(b,"SERVICE_CHARGE","10",null);assertThrows(BusinessException.class,()->service.cancel(folio(b),source.id(),cancellation()));var credit=post(b,"DISCOUNT","-1",source.id());
        assertThrows(BusinessException.class,()->service.register(folio(b),expense("FEE_REVERSAL","-1",credit.id())));invariants(b);}
    @Test void creditBalanceStillBlocksCheckout(){long b=checkIn();var source=post(b,"SERVICE_CHARGE","10",null);pay(b,"310");post(b,"FEE_REVERSAL","-10",source.id());clock.day(3);assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);}
    @Test void finalizationRejectsNewRegistrationAndEvenConfirmationRetry(){long b=checkIn();var e=post(b,"SERVICE_CHARGE","10",null);pay(b,"310");clock.day(3);bookings.checkOut(b,2L);
        assertThrows(BusinessException.class,()->service.confirm(folio(b),e.id()));assertThrows(BusinessException.class,()->service.register(folio(b),expense("SERVICE_CHARGE","1",null)));invariants(b);}
    @ParameterizedTest @ValueSource(strings={"REGISTER","CONFIRM","CANCEL"}) void auditFailureRollsBackExpenseLedgerAndSummary(String operation){long b=checkIn();var r=expense("SERVICE_CHARGE","10",null);var e=operation.equals("REGISTER")?null:service.register(folio(b),r);
        gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);assertThrows(Exception.class,()->{switch(operation){case "REGISTER"->service.register(folio(b),r);case "CONFIRM"->service.confirm(folio(b),e.id());default->service.cancel(folio(b),e.id(),cancellation());}});
        var view=queries.byBooking(b,1L);assertEquals(300,view.totalAmount().intValueExact());assertEquals(operation.equals("REGISTER")?0:1,view.expenses().size());if(e!=null)assertEquals("PENDING",view.expenses().get(0).status());invariants(b);}
    @Test void databaseRejectsDuplicateKeyAndFactMutation(){long b=checkIn();var r=expense("SERVICE_CHARGE","10",null);var e=service.register(folio(b),r);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE expense_registration SET amount=11 WHERE id=?",e.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO expense_registration(folio_id,request_key,item_type,amount,business_date,description,reason,status,registered_by,registered_time) SELECT folio_id,request_key,item_type,amount,business_date,description,reason,status,registered_by,registered_time FROM expense_registration WHERE id=?",e.id()));
        service.confirm(folio(b),e.id());assertThrows(DataAccessException.class,()->jdbc.update("UPDATE expense_registration SET status='PENDING' WHERE id=?",e.id()));invariants(b);}
    @Test void checkoutRejectsLedgerWithoutRegistration(){long b=checkIn();folios.addItem(b,fee("unregistered","10"),2L);pay(b,"310");clock.day(3);assertThrows(BusinessException.class,()->bookings.checkOut(b,2L));invariants(b);}
}
