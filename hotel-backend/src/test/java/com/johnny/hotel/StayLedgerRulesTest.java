package com.johnny.hotel;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.service.support.StayLedgerRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StayLedgerRulesTest {
    LocalDate day=LocalDate.of(2026,10,1);
    Booking booking;List<BookingNightlyRate> rates=new ArrayList<>();List<BookingRoomAssignment> segments=new ArrayList<>();List<FolioItem> items=new ArrayList<>();List<RoomBillingEvent> events=new ArrayList<>();
    void scenario(boolean sameType) {
        booking=new Booking();booking.setId(1L);booking.setRoomTypeId(1L);booking.setAssignedRoomId(2L);booking.setCheckInDate(day);booking.setCheckOutDate(day.plusDays(3));
        segments.add(BookingRoomAssignment.builder().id(1L).bookingId(1L).roomId(1L).roomTypeId(1L).assignmentType("CHECK_IN").startTime(day.atTime(14,0)).endTime(day.plusDays(1).atTime(12,0)).build());
        segments.add(BookingRoomAssignment.builder().id(2L).bookingId(1L).roomId(2L).roomTypeId(sameType?1L:2L).assignmentType("ROOM_CHANGE").startTime(day.plusDays(1).atTime(12,0)).build());
        for(int i=0;i<3;i++) {
            rates.add(BookingNightlyRate.builder().stayDate(day.plusDays(i)).rateAmount(new BigDecimal("100")).build());
            items.add(charge(i+1,1,1,day.plusDays(i),"100"));
            if(i>0){var reverse=charge(i+10,1,1,day.plusDays(i),"-100");reverse.setItemType("ROOM_RATE_ADJUSTMENT");reverse.setSourceItemId((long)i+1);items.add(reverse);
                items.add(charge(i+20,2,sameType?1:2,day.plusDays(i),sameType?"100":"150"));}
        }
        events.add(RoomBillingEvent.builder().id(1L).bookingId(1L).oldAssignmentId(1L).newAssignmentId(2L).changeDate(day.plusDays(1)).newChargesTotal(new BigDecimal(sameType?"200":"300")).build());
    }
    FolioItem charge(long id,long assignment,long type,LocalDate date,String amount){return FolioItem.builder().id(id).roomAssignmentId(assignment).roomId(assignment).roomTypeId(type).itemType("ROOM_CHARGE").businessDate(date).quantity(BigDecimal.ONE).amount(new BigDecimal(amount)).unitPrice(new BigDecimal(amount)).build();}
    @ParameterizedTest @ValueSource(booleans={true,false}) void roomChangeNetAmountAndAllReversalsReconcile(boolean sameType){scenario(sameType);StayLedgerRules.validate(booking,rates,segments,events,items);assertEquals(sameType?300:400,items.stream().map(FolioItem::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add).intValueExact());}
    @ParameterizedTest @ValueSource(strings={"missing","double-credit","event-total","gap","room-type","unexpected-fee","first-price"}) void checkoutRejectsIncompleteOrWrongChangeAccounting(String kind){
        scenario(false);switch(kind){case "missing"->items.remove(0);case "double-credit"->items.add(items.stream().filter(i->i.getSourceItemId()!=null).findFirst().orElseThrow());case "event-total"->events.get(0).setNewChargesTotal(new BigDecimal("301"));
            case "gap"->segments.get(1).setStartTime(day.plusDays(1).atTime(13,0));case "room-type"->segments.get(1).setRoomTypeId(3L);case "unexpected-fee"->items.add(FolioItem.builder().id(99L).itemType("SERVICE_CHARGE").amount(BigDecimal.ONE).build());
            case "first-price"->{items.get(0).setAmount(new BigDecimal("101"));items.get(0).setUnitPrice(new BigDecimal("101"));}}
        assertThrows(BusinessException.class,()->StayLedgerRules.validate(booking,rates,segments,events,items));
    }
}
