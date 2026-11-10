package com.johnny.hotel.businessdate;

import com.johnny.hotel.nightaudit.NightAuditBlocker;
import com.johnny.hotel.nightaudit.NightAuditMapper;
import com.johnny.hotel.nightaudit.NightAuditRun;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HotelTimeContextService {
    private final HotelTimeAuthority time;
    private final BusinessDateService dates;
    private final NightAuditMapper audits;

    public record View(String zoneId, LocalDate hotelDate, ZonedDateTime hotelDateTime,
                       LocalDate businessDate, String businessDateStatus, String reason) {}

    public View current() {
        ZonedDateTime now = time.hotelDateTime();
        LocalDate hotelDate = now.toLocalDate();
        BusinessDateControl control = dates.current();
        LocalDate businessDate = control.getBusinessDate();
        String status;
        String reason = null;

        if (businessDate.isAfter(hotelDate)) {
            status = "BLOCKED";
            reason = "Business Date is ahead of Hotel Date; an operator must investigate.";
        } else if (businessDate.equals(hotelDate) && "OPEN".equals(control.getState())) {
            status = "CURRENT";
        } else {
            NightAuditRun run = audits.byDate(businessDate);
            if (run != null && "BLOCKED".equals(run.getStatus())) {
                status = "BLOCKED";
                List<NightAuditBlocker> blockers = audits.blockers(run.getId());
                reason = blockers.isEmpty() ? run.getLastError() : blockers.get(0).getBlockerType()
                        + " " + blockers.get(0).getObjectType() + " #" + blockers.get(0).getObjectId()
                        + ": " + blockers.get(0).getReason();
            } else if (run != null && "RUNNING".equals(run.getStatus())) {
                status = "CATCHING_UP";
                reason = "Night Audit is processing " + businessDate + ".";
            } else if (run != null && "FAILED".equals(run.getStatus())) {
                status = "BLOCKED";
                reason = "Night Audit failed on " + businessDate + ": " + run.getLastError();
            } else if (businessDate.isBefore(hotelDate.minusDays(1))) {
                status = "STALE";
                reason = "Business Date is " + businessDate
                        + "; Night Audit must catch up to Hotel Date " + hotelDate + ".";
            } else {
                status = "PENDING_AUDIT";
                reason = "Night Audit must advance Business Date " + businessDate + ".";
            }
        }
        return new View(time.zoneId().getId(), hotelDate, now, businessDate, status, reason);
    }
}
