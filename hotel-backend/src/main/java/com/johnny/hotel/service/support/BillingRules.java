package com.johnny.hotel.service.support;

import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class BillingRules {
    private BillingRules() { }
    public static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(message);
    }
    public static void one(int rows) { require(rows == 1, "Concurrent change or missing record"); }
    public static BigDecimal money(BigDecimal value, int precision) {
        require(value != null, "Amount is required");
        try {
            BigDecimal result = value.setScale(2, RoundingMode.UNNECESSARY);
            require(result.precision() <= precision, "Amount exceeds supported range");
            return result;
        } catch (ArithmeticException e) { throw new BusinessException("Amount must have at most two decimal places"); }
    }
    public static void dates(LocalDate in, LocalDate out) {
        require(in != null && out != null && out.isAfter(in), "Check-out date must be after check-in date");

    }
    public static void snapshot(Booking b, BookingPriceVersion version, List<BookingNightlyRate> rates, String currency) {
        dates(b.getCheckInDate(), b.getCheckOutDate());
        require(version != null && Objects.equals(version.getBookingId(), b.getId()) && Objects.equals(version.getIsActive(), 1), "Active price version is missing");
        require(Objects.equals(currency, version.getCurrency()), "Contract and folio currency mismatch");
        require(rates != null && rates.size() == ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()), "Nightly price coverage is incomplete");
        Set<LocalDate> dates = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BookingNightlyRate rate : rates) {
            require(Objects.equals(rate.getPriceVersionId(), version.getId()) && Objects.equals(rate.getBookingId(), b.getId())
                    && Objects.equals(rate.getRoomTypeId(), b.getRoomTypeId()), "Nightly rate ownership or room type mismatch");
            LocalDate d = rate.getStayDate();
            require(d != null && !d.isBefore(b.getCheckInDate()) && d.isBefore(b.getCheckOutDate()) && dates.add(d), "Duplicate or out-of-range nightly rate");
            require(money(rate.getRateAmount(), 10).signum() > 0, "Nightly rate must be positive");
            total = total.add(rate.getRateAmount());
        }
        require(money(total, 10).compareTo(money(version.getTotalPrice(), 10)) == 0
                && total.compareTo(money(b.getTotalPrice(), 10)) == 0, "Booking price snapshot totals do not match");
    }
    public static void item(FolioItemCommand c) {
        require(c != null, "Folio item is required");
        require(c.getItemType() != null && Set.of("ROOM_CHARGE", "ROOM_RATE_ADJUSTMENT", "SERVICE_CHARGE", "DAMAGE_CHARGE", "DISCOUNT", "FEE_REVERSAL", "EARLY_CHECKOUT_REVERSAL", "LATE_CHECKOUT_FEE", "LATE_CHECKOUT_CONFLICT_FEE", "STAY_FEE_REVERSAL").contains(c.getItemType()), "Unsupported folio item type");
        require(c.getDescription() != null && !c.getDescription().isBlank() && c.getDescription().length() <= 500, "Description is required and limited to 500 characters");
        require(c.getBusinessDate() != null, "Business date is required");
        BigDecimal amount = money(c.getAmount(), 12);
        require(amount.signum() != 0, "Folio item amount cannot be zero");
        BigDecimal quantity = money(c.getQuantity(), 10), unit = money(c.getUnitPrice(), 12);
        require(quantity.signum() > 0 && quantity.multiply(unit).compareTo(amount) == 0, "Quantity times unit price must equal amount");
        boolean credit = Set.of("ROOM_RATE_ADJUSTMENT", "DISCOUNT", "FEE_REVERSAL", "EARLY_CHECKOUT_REVERSAL", "STAY_FEE_REVERSAL").contains(c.getItemType());
        require(credit == (amount.signum() < 0), "Item type and amount sign disagree");
        require(!"ROOM_RATE_ADJUSTMENT".equals(c.getItemType()) || c.getSourceItemId() != null, "Room reversal requires source item");
        require(!credit || c.getSourceItemId() != null, "Credits require an original charge");
        require(c.getSourceItemId() == null || amount.signum() < 0, "Source reference is only supported for credits");
        require(c.getEventKey() == null || !c.getEventKey().isBlank() && c.getEventKey().length() <= 100, "Invalid ledger event key");
    }
}
