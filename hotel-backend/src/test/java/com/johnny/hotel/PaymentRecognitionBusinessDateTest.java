package com.johnny.hotel;

import com.johnny.hotel.businessdate.BusinessDateService;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.payment.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRecognitionBusinessDateTest {
    private PaymentAttempt attempt(String status,LocalDate recognized){return PaymentAttempt.builder().id(7L).provider("MOCK").merchantPaymentNo("merchant-7")
            .providerPaymentId("provider-7").amount(new BigDecimal("100.00")).currency("CNY").status(status).initiatedByUserId(3L).recognizedBusinessDate(recognized).build();}
    private ProviderPaymentResult success(){return new ProviderPaymentResult("MOCK","merchant-7","provider-7","SUCCEEDED",new BigDecimal("100.00"),"CNY",LocalDateTime.of(2026,9,22,23,59));}

    @Test void firstTrustedSuccessFreezesCurrentBusinessDate() {
        var db=mock(PaymentCoreMapper.class);var audits=mock(SysAuditLogMapper.class);var dates=mock(BusinessDateService.class);
        when(db.lockClaimed(7L,"claim")).thenReturn(attempt("PENDING",null));when(dates.postingDate()).thenReturn(LocalDate.of(2026,9,22));
        when(db.succeed(7L,"provider-7",LocalDate.of(2026,9,22))).thenReturn(1);when(audits.insert(any())).thenReturn(1);
        assertEquals(7L,new PaymentRecognitionService(db,audits,dates).recognizeQuery(7L,"claim",success()));
        verify(db).succeed(7L,"provider-7",LocalDate.of(2026,9,22));
    }

    @Test void duplicateSuccessAfterDateAdvanceCannotOverwriteFrozenDate() {
        var db=mock(PaymentCoreMapper.class);var audits=mock(SysAuditLogMapper.class);var dates=mock(BusinessDateService.class);
        when(db.lockClaimed(7L,"claim")).thenReturn(attempt("SUCCEEDED",LocalDate.of(2026,9,22)));when(audits.insert(any())).thenReturn(1);
        assertEquals(7L,new PaymentRecognitionService(db,audits,dates).recognizeQuery(7L,"claim",success()));
        verifyNoInteractions(dates);verify(db,never()).succeed(anyLong(),any(),any());
    }
}
