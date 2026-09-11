package com.johnny.hotel;
import com.johnny.hotel.support.IsolatedMysqlTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests", matches="true")
class DatabaseConstraintTest extends IsolatedMysqlTest {
    @Test void oneFolioAndOneActivePriceVersionPerBooking(){long b=create();
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio(booking_id,currency) VALUES(?,'CNY')",b));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO booking_price_version(booking_id,version_no,change_type,is_active,total_price,currency) VALUES(?,2,'ORIGINAL_BOOKING',1,300,'CNY')",b));invariants(b);
    }
    @Test void oneActiveAssignmentPerBookingAndRoom(){long b=checkIn(),other=create();
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO booking_room_assignment(booking_id,room_id,room_type_id,assignment_type,start_time) VALUES(?,2,1,'CHECK_IN',NOW())",b));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO booking_room_assignment(booking_id,room_id,room_type_id,assignment_type,start_time) VALUES(?,1,1,'CHECK_IN',NOW())",other));invariants(b);invariants(other);
    }
    @Test void onlyOneLiveReservationPerRoom(){long b=checkIn(),other=create();
        // V12: future (APPROVED) reservations may share a room; only actual occupancy (CHECKED_IN) is exclusive.
        var approveOther=new com.johnny.hotel.dto.ApproveBookingRequest();approveOther.setAssignedRoomId(2L);bookings.approveBooking(other,approveOther,2L);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking SET assigned_room_id=1,status=2 WHERE id=?",other));invariants(b);invariants(other);
    }
    @Test void nightlyVersionMustBelongToSameBooking(){long b=create(),other=create();long version=jdbc.queryForObject("SELECT id FROM booking_price_version WHERE booking_id=?",Long.class,b);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO booking_nightly_rate(price_version_id,booking_id,stay_date,room_type_id,rate_amount,rate_source) VALUES(?,?,?,1,100,'BASE_PRICE')",version,other,arrival.plusDays(5)));invariants(b);invariants(other);
    }
    @Test void sourceItemCannotCrossFolio(){long b=create(),other=create();var source=folios.addItem(b,fee("s","10"),2L);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount,source_item_id) VALUES(?,'DISCOUNT','test',?,1,-5,-5,?)",folio(other),arrival,source.getId()));invariants(b);invariants(other);
    }
    @Test void duplicateRoomNightAndEventKeyRejected(){long b=checkIn();long f=folio(b),assignment=jdbc.queryForObject("SELECT id FROM booking_room_assignment WHERE booking_id=?",Long.class,b);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount,room_assignment_id) VALUES(?,'ROOM_CHARGE','test',?,1,100,100,?)",f,arrival,assignment));
        folios.addItem(b,fee("unique-event","10"),2L);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount,event_key) VALUES(?,'SERVICE_CHARGE','test',?,1,10,10,'unique-event')",f,arrival));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1"}) void paymentAmountMustBePositive(String amount){long b=create();assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO payment(folio_id,amount,payment_method,status,paid_time,request_key) VALUES(?,?,'CASH','SUCCESS',NOW(),UUID())",folio(b),amount));invariants(b);}
    @Test void paymentKeyRequiredAndUnique(){long b=create();var r=request("10");payments.recordPayment(folio(b),r,2L);
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO payment(folio_id,amount,payment_method,status,paid_time,request_key) VALUES(?,10,'CASH','SUCCESS',NOW(),?)",folio(b),r.getIdempotencyKey()));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO payment(folio_id,amount,payment_method,status,paid_time) VALUES(?,10,'CASH','SUCCESS',NOW())",folio(b)));invariants(b);
    }
    @ParameterizedTest @ValueSource(strings={"zero","product","date"}) void ledgerInsertTriggerRejectsInvalidFacts(String kind){long b=create();
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO folio_item(folio_id,item_type,description,business_date,quantity,unit_price,amount) VALUES(?,'SERVICE_CHARGE','test',?,1,10,?)",folio(b),kind.equals("date")?null:arrival,kind.equals("zero")?0:kind.equals("product")?9:10));invariants(b);
    }
    @Test void historicalPaymentAndLedgerCannotBeOverwritten(){long b=checkIn();pay(b,"10");
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE payment SET status='FAILED' WHERE folio_id=?",folio(b)));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE folio_item SET amount=1 WHERE folio_id=?",folio(b)));invariants(b);
    }
}
