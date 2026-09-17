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
    private final StayRoomAssignmentMapper assignments;
    private final com.johnny.hotel.booking.deposit.DepositTransferService depositTransfers;
    private final com.johnny.hotel.stay.StayAdjustmentMapper stayAdjustments;
    private final com.johnny.hotel.stay.StayCheckoutRules stayRules;
    private final RoomBillingEventMapper events;
    private final FolioMapper folios;
    private final FolioItemMapper items;
    private final PriceSnapshotValidator snapshots;
    private final FolioFinancialService financial;
    private final ExpenseMapper expenses;
    private final com.johnny.hotel.wallet.RefundMapper refunds;
    private final com.johnny.hotel.wallet.WalletCheckoutSettlement walletSettlement;
    private final com.johnny.hotel.extras.ExtrasLedgerRules extrasLedgerRules;
    private final com.johnny.hotel.damage.DamageLedgerRules damageLedgerRules;

    /** Caller owns Booking and current Room; no new Room/Booking locks after Folio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean finalizeStay(com.johnny.hotel.stay.Stay stay,Booking booking, LocalDateTime now,Long actor) {
        require(stay!=null && stay.getStatus()==com.johnny.hotel.stay.StayStatus.IN_HOUSE.getCode()
                && stay.getBookingId().equals(booking.getId()),"Actual Stay does not match reservation");
        var segments = assignments.selectByStayId(stay.getId());
        var changes = events.selectByStayIdForUpdate(stay.getId());
        Folio folio = folios.selectByStayIdForUpdate(stay.getId());
        require(folio != null && folio.getClosedTime() == null, "Folio is missing, closed or void");
        var rates = snapshots.validate(booking, folio.getCurrency());
        var posted = items.selectByFolioIdForUpdate(folio.getId());
        var adjustments=stayAdjustments.forStay(stay.getId());
        var extensionRates=stayAdjustments.nights(stay.getId());
        require(extensionRates.stream().allMatch(n->folio.getCurrency().equals(n.getCurrency())),"Extension currency mismatch");
        var verified=stayRules.validate(booking,folio,segments,adjustments,posted,now);
        var withoutDamage=damageLedgerRules.validate(folio.getId(),verified);
        var withoutExtras=extrasLedgerRules.validate(folio.getId(),withoutDamage);
        var roomLedger = ExpenseRules.checkout(booking,folio.getId(),expenses.selectByFolioForUpdate(folio.getId()),withoutExtras,com.johnny.hotel.stay.StayPlan.end(booking,adjustments));
        StayLedgerRules.validate(booking, rates, segments, changes, roomLedger,adjustments,extensionRates);
        Folio summary = financial.recalculateSummary(folio.getId());
        require(refunds.forFolio(folio.getId()).stream().noneMatch(r->r.getStatus()==com.johnny.hotel.enums.RefundStatus.PENDING.getCode()),"Pending refunds must be resolved before checkout");
        depositTransfers.validateHistory(folio);
        walletSettlement.validateHistory(booking,folio.getId());
        if(summary.getBalanceAmount().signum()>0 && walletSettlement.contribute(booking,summary,actor)) {
            summary=financial.recalculateSummary(folio.getId());
            if(summary.getBalanceAmount().signum()>0)return false;
        }
        require(summary.getBalanceAmount().signum() == 0 && Integer.valueOf(com.johnny.hotel.enums.FolioStatus.COMPLETED.getCode()).equals(summary.getStatus()), "Outstanding debt or credit must be resolved before checkout");
        one(folios.close(folio.getId(), now));
        return true;
    }
}
