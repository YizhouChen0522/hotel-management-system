package com.johnny.hotel;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.service.support.ExpenseRules;
import com.johnny.hotel.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseRulesTest {
    final LocalDate day=LocalDate.of(2026,10,1);
    RegisterExpenseRequest request(){return RegisterExpenseRequest.builder().idempotencyKey(UUID.randomUUID().toString()).itemType("SERVICE_CHARGE").amount(new BigDecimal("10"))
            .businessDate(day).description("laundry").reason("guest order").build();}
    Booking booking(){var b=new Booking();b.setStatus(2);b.setCheckInDate(day);b.setCheckOutDate(day.plusDays(3));return b;}
    @Test void positiveExpenseAndCheckoutDayAllowed(){ExpenseRules.validate(request(),booking(),day);var r=request();r.setBusinessDate(day.plusDays(3));ExpenseRules.validate(r,booking(),day.plusDays(3));}
    @ParameterizedTest @ValueSource(strings={"zero","negative","precision","range","type","date","future","source","reason","uuid"}) void rejectsInvalidInput(String kind){var r=request();switch(kind){
        case "zero"->r.setAmount(BigDecimal.ZERO);case "negative"->r.setAmount(new BigDecimal("-1"));case "precision"->r.setAmount(new BigDecimal("1.001"));case "range"->r.setAmount(new BigDecimal("10000000000"));
        case "type"->r.setItemType("REFUND");case "date"->r.setBusinessDate(day.minusDays(1));case "future"->r.setBusinessDate(day.plusDays(1));case "source"->r.setSourceExpenseId(1L);case "reason"->r.setReason(" ");case "uuid"->r.setIdempotencyKey("1-1-1-1-1");}
        assertThrows(BusinessException.class,()->ExpenseRules.validate(r,booking(),day));}
    @ParameterizedTest @ValueSource(ints={0,1,3,4,5}) void onlyCheckedInSupportsNewExpenses(int state){var b=booking();b.setStatus(state);assertThrows(BusinessException.class,()->ExpenseRules.validate(request(),b,day));}
    @ParameterizedTest @ValueSource(strings={"ROLE_HR_ADMIN","ROLE_CUSTOMER"}) void nonOperatorsCannotPost(String role){assertThrows(AccessDeniedException.class,()->ExpenseRules.authorize("SERVICE_CHARGE",List.of(role)));}
    @ParameterizedTest @ValueSource(strings={"DISCOUNT","FEE_REVERSAL"}) void creditsRequireManagerAndSource(String type){assertThrows(AccessDeniedException.class,()->ExpenseRules.authorize(type,List.of("ROLE_STAFF")));var r=request();r.setItemType(type);r.setAmount(new BigDecimal("-5"));assertThrows(BusinessException.class,()->ExpenseRules.validate(r,booking(),day));r.setSourceExpenseId(1L);ExpenseRules.validate(r,booking(),day);}
    @ParameterizedTest @ValueSource(strings={"ROLE_MANAGER","ROLE_OWNER","ROLE_SUPER_ADMIN"}) void managersMayAdjust(String role){ExpenseRules.authorize("DISCOUNT",List.of(role));ExpenseRules.authorize("FEE_REVERSAL",List.of(role));}
    @Test void pendingCreditsReserveSourceAndCancelledCreditsReleaseIt(){var source=ExpenseRegistration.builder().id(1L).folioId(1L).status("CONFIRMED").itemType("SERVICE_CHARGE").amount(new BigDecimal("10")).businessDate(day).build();
        var first=ExpenseRegistration.builder().id(2L).folioId(1L).status("PENDING").itemType("DISCOUNT").amount(new BigDecimal("-6")).businessDate(day).sourceExpenseId(1L).build();
        var next=ExpenseRegistration.builder().folioId(1L).status("PENDING").itemType("FEE_REVERSAL").amount(new BigDecimal("-5")).businessDate(day).sourceExpenseId(1L).build();
        assertThrows(BusinessException.class,()->ExpenseRules.sourceAndCapacity(next,List.of(source,first)));first.setStatus("CANCELLED");assertEquals(source,ExpenseRules.sourceAndCapacity(next,List.of(source,first)));
        next.setFolioId(2L);assertThrows(BusinessException.class,()->ExpenseRules.sourceAndCapacity(next,List.of(source)));}
}
