package com.johnny.hotel;
import com.johnny.hotel.dto.RecordPaymentRequest;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.service.impl.PaymentServiceImpl;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.vo.PaymentVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock BookingMapper bookings;@Mock FolioMapper folios;@Mock PaymentMapper payments;@Mock FolioFinancialService financial;@Mock SysAuditLogMapper audits;
    PaymentServiceImpl service;
    final String key="123e4567-e89b-12d3-a456-426614174000";
    @BeforeEach void setup(){service=new PaymentServiceImpl(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),bookings,folios,payments,financial,audits);}
    RecordPaymentRequest request(){return RecordPaymentRequest.builder().amount(new BigDecimal("10")).paymentMethod("cash").idempotencyKey(key).build();}
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","10000000000.00"}) void invalidAmountNeverTouchesDatabase(String amount){var r=request();r.setAmount(new BigDecimal(amount));assertThrows(BusinessException.class,()->service.recordPayment(8L,r,2L));verifyNoInteractions(folios,payments);}
    @ParameterizedTest @ValueSource(strings={"","1-1-1-1-1","not-uuid","123e4567-e89b-12d3-a456-4266141740000"}) void rejectsNoncanonicalUuid(String value){var r=request();r.setIdempotencyKey(value);assertThrows(BusinessException.class,()->service.recordPayment(8L,r,2L));verifyNoInteractions(payments);}
    Payment existing(){return Payment.builder().id(3L).folioId(8L).amount(new BigDecimal("10.00")).paymentMethod("CASH").status("SUCCESS").requestKey(key).build();}
    void stub(){when(folios.selectByIdForUpdate(8L)).thenReturn(Folio.builder().id(8L).bookingId(1L).status("OPEN").build());when(payments.selectByFolioIdAndRequestKey(8L,key)).thenReturn(existing());}
    @Test void retryNormalizesUuidMethodAndAmountWithoutNewWrites(){stub();var r=request();r.setIdempotencyKey(key.toUpperCase());r.setPaymentMethod(" cash ");assertEquals(3L,service.recordPayment(8L,r,2L).getId());verify(payments,never()).insert(any());verifyNoInteractions(financial,audits,bookings);}
    @ParameterizedTest @ValueSource(strings={"amount","method","reference","note"}) void sameKeyDifferentContentRejected(String field){stub();var r=request();switch(field){case "amount"->r.setAmount(new BigDecimal("11"));case "method"->r.setPaymentMethod("OTHER");case "reference"->r.setReferenceNo("other");case "note"->r.setNote("other");}assertThrows(BusinessException.class,()->service.recordPayment(8L,r,2L));verify(payments,never()).insert(any());}
    @Test void newPaymentAfterFinalizationRejected(){when(folios.selectByIdForUpdate(8L)).thenReturn(Folio.builder().id(8L).closedTime(LocalDateTime.now()).status("SETTLED").build());assertThrows(BusinessException.class,()->service.recordPayment(8L,request(),2L));verify(payments,never()).insert(any());}
    @Test void paymentVoUsesFolioIdAndOmitsInternalRequestKey(){var v=PaymentVO.from(existing());assertEquals(8L,v.getFolioId());assertEquals(3L,v.getId());assertEquals(new BigDecimal("10.00"),v.getAmount());assertTrue(java.util.Arrays.stream(PaymentVO.class.getDeclaredFields()).noneMatch(f->f.getName().equals("requestKey")));}
}
