package com.johnny.hotel;

import com.johnny.hotel.businessdate.BusinessDateControl;
import com.johnny.hotel.businessdate.BusinessDateService;
import com.johnny.hotel.businessdate.HotelTimeAuthority;
import com.johnny.hotel.businessdate.HotelTimeContextService;
import com.johnny.hotel.nightaudit.NightAuditBlocker;
import com.johnny.hotel.nightaudit.NightAuditMapper;
import com.johnny.hotel.nightaudit.NightAuditRun;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class HotelTimeContextServiceTest {
    @Test
    void blockedAuditReportsDateAndBusinessObject() {
        var dates = mock(BusinessDateService.class);
        var audits = mock(NightAuditMapper.class);
        var businessDate = LocalDate.of(2026, 10, 4);
        when(dates.current()).thenReturn(BusinessDateControl.builder()
                .businessDate(businessDate).state("OPEN").build());
        when(audits.byDate(businessDate)).thenReturn(NightAuditRun.builder()
                .id(13L).businessDate(businessDate).status("BLOCKED").build());
        when(audits.blockers(13L)).thenReturn(List.of(NightAuditBlocker.builder()
                .blockerType("OVERDUE_DEPARTURE").objectType("STAY")
                .objectId(854L).reason("Explicit resolution required").build()));
        var clock = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneId.of("America/New_York"));

        var view = new HotelTimeContextService(new HotelTimeAuthority(clock), dates, audits).current();

        assertEquals("BLOCKED", view.businessDateStatus());
        assertEquals(LocalDate.of(2026, 10, 28), view.hotelDate());
        assertEquals(businessDate, view.businessDate());
        assertTrue(view.reason().contains("STAY #854"));
    }
}
