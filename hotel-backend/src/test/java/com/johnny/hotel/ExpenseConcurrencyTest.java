package com.johnny.hotel;
import com.johnny.hotel.support.*;
import com.johnny.hotel.service.ExpenseService;
import com.johnny.hotel.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.johnny.hotel.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests",matches="true")
class ExpenseConcurrencyTest extends IsolatedMysqlTest {
    @Autowired ExpenseService service;
    RegisterExpenseRequest expense(String type,String amount,Long source){return RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType(type).amount(new BigDecimal(amount)).businessDate(arrival).description("test").reason("test").sourceExpenseId(source).build();}
    long pending(long b){ExpenseIntegrationTest.actor("MANAGER");try{return service.register(folio(b),expense("SERVICE_CHARGE","10",null)).id();}finally{SecurityContextHolder.clearContext();}}
    void confirm(long b,long e){ExpenseIntegrationTest.actor("MANAGER");try{service.confirm(folio(b),e);}finally{SecurityContextHolder.clearContext();}}
    Future<Throwable> worker(ExecutorService pool,String name,Runnable action){return pool.submit(()->{Thread.currentThread().setName(name);ExpenseIntegrationTest.actor("MANAGER");try{action.run();return null;}catch(Throwable e){return e;}finally{SecurityContextHolder.clearContext();}});}
    Throwable await(Future<Throwable> task){try{return task.get(20,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}}
    Throwable pair(String statement,Runnable first,Runnable second){var pool=Executors.newFixedThreadPool(2);gate.arm("expense-first",statement,false);
        try{var a=worker(pool,"expense-first",first);SqlGate.await(gate.reached);var b=worker(pool,"expense-second",second);boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<deadline){if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.innodb_lock_waits",Integer.class)>0){waiting=true;break;}java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));}
            assertTrue(waiting,"Expected real InnoDB lock wait");gate.release.countDown();assertNull(await(a));return await(b);
        }finally{gate.clear();pool.shutdown();try{assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}}
    void rejected(Throwable e){assertNotNull(e);while(e.getCause()!=null)e=e.getCause();assertInstanceOf(BusinessException.class,e);}
    @Test void concurrentRegistrationSameKeyCreatesOnePendingFact(){long b=checkIn();var r=expense("SERVICE_CHARGE","10",null);assertNull(pair("ExpenseMapper.insert",()->service.register(folio(b),r),()->service.register(folio(b),r)));assertEquals(1,queries.byBooking(b,1L).expenses().size());invariants(b);}
    @Test void repeatedConfirmationHasOneLedgerEntry(){long b=checkIn(),e=pending(b);assertNull(pair("ExpenseMapper.resolve",()->service.confirm(folio(b),e),()->service.confirm(folio(b),e)));assertEquals(310,queries.byBooking(b,1L).totalAmount().intValueExact());assertEquals(4,queries.byBooking(b,1L).items().size());invariants(b);}
    @ParameterizedTest @ValueSource(booleans={true,false}) void paymentAndConfirmationBothOrdersRemainConsistent(boolean expenseFirst){long b=checkIn(),e=pending(b);pay(b,"300");
        if(expenseFirst)assertNull(pair("ExpenseMapper.resolve",()->service.confirm(folio(b),e),()->pay(b,"10")));
        else assertNull(pair("FolioMapper.selectByIdForUpdate",()->pay(b,"10"),()->service.confirm(folio(b),e)));
        invariants(b);clock.day(3);bookings.checkOut(b,2L);invariants(b);}
    @Test void registrationWinsCheckoutSeesPendingFee(){long b=checkIn();pay(b,"300");clock.day(3);rejected(pair("ExpenseMapper.insert",()->service.register(folio(b),expense("SERVICE_CHARGE","10",null)),()->bookings.checkOut(b,2L)));assertEquals(2,state(b));invariants(b);}
    @ParameterizedTest @ValueSource(booleans={true,false}) void checkoutWinsBlocksRegistrationAndConfirmationRetry(boolean register){long b=checkIn(),e=pending(b);confirm(b,e);pay(b,"310");clock.day(3);
        rejected(pair("FolioMapper.close",()->bookings.checkOut(b,2L),()->{if(register)service.register(folio(b),expense("SERVICE_CHARGE","1",null));else service.confirm(folio(b),e);}));invariants(b);}
    @Test void cancellingLastPendingExpenseAllowsWaitingCheckout(){long b=checkIn(),e=pending(b);pay(b,"300");clock.day(3);var r=CancelExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).reason("error").build();
        assertNull(pair("ExpenseMapper.resolve",()->service.cancel(folio(b),e,r),()->bookings.checkOut(b,2L)));invariants(b);}
    @Test void simultaneousCreditsCannotExceedOriginalCharge(){long b=checkIn(),e=pending(b);confirm(b,e);rejected(pair("ExpenseMapper.insert",()->service.register(folio(b),expense("DISCOUNT","-7",e)),()->service.register(folio(b),expense("FEE_REVERSAL","-7",e))));assertEquals(2,queries.byBooking(b,1L).expenses().size());invariants(b);}
}
