package com.johnny.hotel;
import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.service.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BillingRulesTest {
    final LocalDate day=LocalDate.of(2026,10,1);
    Booking booking() { Booking b=new Booking();b.setId(1L);b.setRoomTypeId(1L);b.setCheckInDate(day);b.setCheckOutDate(day.plusDays(2));b.setTotalPrice(new BigDecimal("200"));return b; }
    BookingPriceVersion version(){return BookingPriceVersion.builder().id(1L).bookingId(1L).isActive(1).totalPrice(new BigDecimal("200")).currency("CNY").build();}
    List<BookingNightlyRate> rates(){return new ArrayList<>(List.of(BookingNightlyRate.builder().priceVersionId(1L).bookingId(1L).roomTypeId(1L).stayDate(day).rateAmount(new BigDecimal("100")).build(),
            BookingNightlyRate.builder().priceVersionId(1L).bookingId(1L).roomTypeId(1L).stayDate(day.plusDays(1)).rateAmount(new BigDecimal("100")).build()));}
    FolioItemCommand item(){return FolioItemCommand.builder().itemType("SERVICE_CHARGE").description("service").businessDate(day).quantity(new BigDecimal("2")).unitPrice(new BigDecimal("5")).amount(new BigDecimal("10")).build();}
    @Test void exactMoneyAndCompleteSnapshotAccepted(){assertEquals(new BigDecimal("1.20"),BillingRules.money(new BigDecimal("1.200"),12));BillingRules.snapshot(booking(),version(),rates(),"CNY");BillingRules.item(item());}
    @ParameterizedTest @ValueSource(strings={"0.001","10000000000.00","-10000000000.00"}) void rejectsUnrepresentableMoney(String amount){assertThrows(BusinessException.class,()->BillingRules.money(new BigDecimal(amount),12));}
    @ParameterizedTest @ValueSource(ints={0,-1}) void rejectsInvalidDateRange(int nights){assertThrows(BusinessException.class,()->BillingRules.dates(day,day.plusDays(nights)));}
    @ParameterizedTest @ValueSource(strings={"missing","duplicate","date","type","booking","version","amount","total","currency","inactive"}) void rejectsCorruptSnapshot(String kind){
        var b=booking();var v=version();var r=rates();
        switch(kind){case "missing"->r.remove(0);case "duplicate"->r.get(1).setStayDate(day);case "date"->r.get(1).setStayDate(day.plusDays(2));case "type"->r.get(0).setRoomTypeId(2L);
            case "booking"->r.get(0).setBookingId(2L);case "version"->r.get(0).setPriceVersionId(2L);case "amount"->r.get(0).setRateAmount(new BigDecimal("100.001"));
            case "total"->b.setTotalPrice(new BigDecimal("201"));case "currency"->v.setCurrency("USD");case "inactive"->v.setIsActive(0);}
        assertThrows(BusinessException.class,()->BillingRules.snapshot(b,v,r,"CNY"));
    }
    @ParameterizedTest @ValueSource(strings={"zero","precision","quantity","product","type","date","sign","source","description","event"}) void rejectsInvalidItem(String kind){
        var c=item();switch(kind){case "zero"->c.setAmount(BigDecimal.ZERO);case "precision"->c.setAmount(new BigDecimal("10.001"));case "quantity"->c.setQuantity(BigDecimal.ZERO);
            case "product"->c.setUnitPrice(new BigDecimal("6"));case "type"->c.setItemType("REFUND");case "date"->c.setBusinessDate(null);case "sign"->c.setItemType("DISCOUNT");
            case "source"->c.setSourceItemId(1L);case "description"->c.setDescription(" ");case "event"->c.setEventKey(" ");}
        assertThrows(BusinessException.class,()->BillingRules.item(c));
    }
    @Test void checkoutRequiresHistoryEvenWithEmptyLedger(){assertThrows(BusinessException.class,()->StayLedgerRules.validate(booking(),rates(),List.of(),List.of(),List.of()));}
}
