package com.johnny.hotel;

import com.johnny.hotel.businessdate.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessDateServiceTest {
    @Test void firstInitializationUsesInjectedClockExactlyOnce() {
        var db=mock(BusinessDateMapper.class);
        var clock=Clock.fixed(Instant.parse("2026-09-23T17:30:00Z"),ZoneId.of("Asia/Shanghai"));
        when(db.lockExclusive()).thenReturn(BusinessDateControl.builder().id(1).state("OPEN").build());
        when(db.initialize(any(),any())).thenReturn(1);when(db.history(any(),any())).thenReturn(1);
        new BusinessDateService(db,clock).initializeOnce();
        verify(db).initialize(eq(LocalDate.of(2026,9,24)),eq(LocalDateTime.of(2026,9,24,1,30)));
        verify(db).history(eq(LocalDate.of(2026,9,24)),eq(LocalDateTime.of(2026,9,24,1,30)));
    }

    @Test void initializedDateDoesNotFollowLaterClockOrRestart() {
        var db=mock(BusinessDateMapper.class);
        when(db.lockExclusive()).thenReturn(BusinessDateControl.builder().id(1).businessDate(LocalDate.of(2026,9,22)).state("OPEN").build());
        new BusinessDateService(db,Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"),ZoneOffset.UTC)).initializeOnce();
        verify(db,never()).initialize(any(),any());verify(db,never()).history(any(),any());
    }

    @Test void postingUsesPersistedDateRatherThanWallClockDate() {
        var db=mock(BusinessDateMapper.class);
        when(db.lockForPosting()).thenReturn(BusinessDateControl.builder().id(1).businessDate(LocalDate.of(2026,9,22)).state("OPEN").build());
        var service=new BusinessDateService(db,Clock.fixed(Instant.parse("2026-09-23T17:30:00Z"),ZoneId.of("Asia/Shanghai")));
        assertEquals(LocalDate.of(2026,9,22),service.postingDate());
    }
    @Test void closingRejectsOrdinaryPostingButAllowsOnlyMatchingAuditDate(){var db=mock(BusinessDateMapper.class);var date=LocalDate.of(2026,9,22);when(db.lockForPosting()).thenReturn(BusinessDateControl.builder().businessDate(date).state("CLOSING").build());var service=new BusinessDateService(db,Clock.systemUTC());assertThrows(com.johnny.hotel.exception.BusinessException.class,service::postingDate);assertEquals(date,service.closingPostingDate(date));assertThrows(com.johnny.hotel.exception.BusinessException.class,()->service.closingPostingDate(date.minusDays(1)));}
}
