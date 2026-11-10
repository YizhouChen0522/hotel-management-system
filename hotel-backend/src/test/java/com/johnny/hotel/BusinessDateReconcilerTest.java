package com.johnny.hotel;

import com.johnny.hotel.businessdate.BusinessDateControl;
import com.johnny.hotel.businessdate.BusinessDateReconciler;
import com.johnny.hotel.businessdate.BusinessDateService;
import com.johnny.hotel.businessdate.HotelTimeAuthority;
import com.johnny.hotel.businessdate.HotelTimeContextService;
import com.johnny.hotel.nightaudit.NightAuditRun;
import com.johnny.hotel.nightaudit.NightAuditService;
import com.johnny.hotel.nightaudit.NightAuditSystemActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BusinessDateReconcilerTest {
    @Test
    void catchesUpDayByDayAndStopsAtBlocker() throws Exception {
        var dates = mock(BusinessDateService.class);
        var audits = mock(NightAuditService.class);
        var context = mock(HotelTimeContextService.class);
        var systemActor = mock(NightAuditSystemActor.class);
        when(systemActor.id()).thenReturn(7L);
        var time = new HotelTimeAuthority(Clock.fixed(
                Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/New_York")));
        var start = LocalDate.of(2026, 10, 24);
        when(dates.current()).thenReturn(
                BusinessDateControl.builder().businessDate(start).state("OPEN").build(),
                BusinessDateControl.builder().businessDate(start.plusDays(1)).state("OPEN").build());
        when(audits.execute("hotel-time-reconcile-2026-10-24", 7L)).thenReturn(
                new NightAuditService.View(NightAuditRun.builder().businessDate(start).status("COMPLETED").build(), List.of()));
        when(audits.execute("hotel-time-reconcile-2026-10-25", 7L)).thenReturn(
                new NightAuditService.View(NightAuditRun.builder().businessDate(start.plusDays(1)).status("BLOCKED").build(), List.of()));
        when(context.current()).thenReturn(new HotelTimeContextService.View(
                "America/New_York", time.hotelDate(), time.hotelDateTime(),
                start.plusDays(1), "BLOCKED", "Pending arrival"));

        var reconciler = new BusinessDateReconciler(time, dates, audits, context, systemActor);
        reconciler.reconcile();

        verify(audits).execute("hotel-time-reconcile-2026-10-24", 7L);
        verify(audits).execute("hotel-time-reconcile-2026-10-25", 7L);
        verifyNoMoreInteractions(audits);
        assertEquals(start.plusDays(1), dates.current().getBusinessDate());
    }
}
