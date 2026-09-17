package com.johnny.hotel.wallet;
import com.johnny.hotel.support.IsolatedMysqlTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConstraintTest extends FinancialDevelopmentFixture {
    com.johnny.hotel.dto.FolioItemCommand fee(String key,String amount){return com.johnny.hotel.dto.FolioItemCommand.builder().itemType("SERVICE_CHARGE").description("test service").businessDate(arrival).quantity(java.math.BigDecimal.ONE).unitPrice(new java.math.BigDecimal(amount)).amount(new java.math.BigDecimal(amount)).eventKey(key).build();}

    @Test void oneFolioAndOneActivePriceVersionPerBooking(){long b=stay();
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio(stay_id,currency) VALUES(?,'CNY')",sid(b)));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO booking_price_version(booking_id,version_no,change_type,is_active,total_price,currency) VALUES(?,2,'ORIGINAL_BOOKING',1,300,'CNY')",b));invariantBooking(b);
    }
    @Test void oneActiveAssignmentPerBookingAndRoom(){long b=stay(),other=stay("OTHER_CUSTOMER");
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO stay_room_assignment(stay_id,room_id,room_type_id,assignment_type,start_time) VALUES(?,?,?,'CHECK_IN',NOW())",sid(b),room2,type1));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO stay_room_assignment(stay_id,room_id,room_type_id,assignment_type,start_time) VALUES(?,?,?,'CHECK_IN',NOW())",sid(other),room1,type1));invariantBooking(b);
    }
    @Test void bookingCannotCarryActualStayStatus(){long b=stay();
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking SET status=2 WHERE id=?",b));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking SET check_out_date=DATE_ADD(check_out_date,INTERVAL 1 DAY) WHERE id=?",b));invariantBooking(b);
    }
    @Test void nightlyVersionMustBelongToSameBooking(){long b=stay(),other=stay("OTHER_CUSTOMER");long version=jdbc.queryForObject("SELECT id FROM booking_price_version WHERE booking_id=?",Long.class,b);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO booking_nightly_rate(price_version_id,booking_id,stay_date,room_type_id,rate_amount,rate_source) VALUES(?,?,?,?,100,'BASE_PRICE')",version,other,arrival.plusDays(5),type1));invariantBooking(b);
    }
    @Test void sourceItemCannotCrossFolio(){long b=stay(),other=stay("OTHER_CUSTOMER");var source=folios.addItem(sid(b),fee("s","10"),uid("MANAGER"));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount,source_item_id) VALUES(?,'DISCOUNT','test',?,1,-5,-5,?)",folio(other),arrival,source.getId()));invariantBooking(b);
    }
    @Test void duplicateRoomNightAndEventKeyRejected(){long b=stay();long f=folio(b),assignment=jdbc.queryForObject("SELECT id FROM stay_room_assignment WHERE stay_id=(SELECT id FROM stay WHERE booking_id=?)",Long.class,b);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount,room_assignment_id) VALUES(?,'ROOM_CHARGE','test',?,1,100,100,?)",f,arrival,assignment));
        folios.addItem(sid(b),fee("unique-event","10"),uid("MANAGER"));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount,event_key) VALUES(?,'SERVICE_CHARGE','test',?,1,10,10,'unique-event')",f,arrival));invariantBooking(b);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1"}) void paymentAmountMustBePositive(String amount){long b=stay();assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO payment(folio_id,amount,payment_method,status,paid_time,request_key) VALUES(?,?,'CASH','SUCCESS',NOW(),UUID())",folio(b),amount));invariantBooking(b);}
    @Test void paymentKeyRequiredAndUnique(){long b=stay();var r=payRequest("10");payments.recordPayment(folio(b),r,uid("MANAGER"));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO payment(folio_id,amount,payment_method,status,paid_time,request_key) VALUES(?,10,'CASH','SUCCESS',NOW(),?)",folio(b),r.getIdempotencyKey()));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO payment(folio_id,amount,payment_method,status,paid_time) VALUES(?,10,'CASH','SUCCESS',NOW())",folio(b)));invariantBooking(b);
    }
    @ParameterizedTest @ValueSource(strings={"zero","product","date"}) void ledgerInsertTriggerRejectsInvalidFacts(String kind){long b=stay();
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount) VALUES(?,'SERVICE_CHARGE','test',?,1,10,?)",folio(b),kind.equals("date")?null:arrival,kind.equals("zero")?0:kind.equals("product")?9:10));invariantBooking(b);
    }
    @Test void historicalPaymentAndLedgerCannotBeOverwritten(){long b=stay();pay(b,"10");
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE payment SET status='FAILED' WHERE folio_id=?",folio(b)));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE folio_item SET amount=1 WHERE folio_id=?",folio(b)));invariantBooking(b);
    }
}
