package com.johnny.hotel.service.support;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.FolioFinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static com.johnny.hotel.service.support.BillingRules.*;

@Component
@RequiredArgsConstructor
public class CheckoutFinalizer {
    private final BookingRoomAssignmentMapper assignments;
    private final RoomBillingEventMapper events;
    private final FolioMapper folios;
    private final FolioItemMapper items;
    private final PriceSnapshotValidator snapshots;
    private final FolioFinancialService financial;
    private final ExpenseMapper expenses;

    /** Caller owns Booking and current Room; no new Room/Booking locks after Folio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeStay(Booking booking, LocalDateTime now) {
        var segments = assignments.selectByBookingId(booking.getId());
        var changes = events.selectByBookingIdForUpdate(booking.getId());
        Folio folio = folios.selectByBookingIdForUpdate(booking.getId());
        require(folio != null && folio.getClosedTime() == null && !"VOID".equals(folio.getStatus()), "Folio is missing, closed or void");
        var rates = snapshots.validate(booking, folio.getCurrency());
        var posted = items.selectByFolioIdForUpdate(folio.getId());
        var roomLedger = ExpenseRules.checkout(booking,folio.getId(),expenses.selectByFolioForUpdate(folio.getId()),posted);
        StayLedgerRules.validate(booking, rates, segments, changes, roomLedger);
        Folio summary = financial.recalculateSummary(booking.getId());
        require(summary.getBalanceAmount().signum() == 0 && "SETTLED".equals(summary.getStatus()), "Outstanding debt or credit must be resolved before checkout");
        one(folios.close(folio.getId(), now));
    }
}
