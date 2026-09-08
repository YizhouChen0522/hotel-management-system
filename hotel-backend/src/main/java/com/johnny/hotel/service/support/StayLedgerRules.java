package com.johnny.hotel.service.support;

import com.johnny.hotel.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Verifies posted facts, never reconstructs missing financial history. */
public final class StayLedgerRules {
    private StayLedgerRules() {}

    public static void validate(Booking booking, List<BookingNightlyRate> snapshot,
            List<BookingRoomAssignment> segments, List<RoomBillingEvent> events, List<FolioItem> items) {
        require(!segments.isEmpty(), "Stay history is missing");
        require(events.size() == segments.size() - 1, "Room change billing events are incomplete");
        Set<Long> consumed = new HashSet<>();
        Map<LocalDate, BigDecimal> previous = new HashMap<>();
        for (BookingNightlyRate n : snapshot) previous.put(n.getStayDate(), n.getRateAmount());
        Set<Long> eventIds = new HashSet<>();
        for (int index = 0; index < segments.size(); index++) {
            BookingRoomAssignment segment = segments.get(index);
            require(segment.getBookingId().equals(booking.getId()), "Stay history belongs to another booking");
            LocalDate from = segment.getStartTime().toLocalDate();
            require(!from.isBefore(booking.getCheckInDate()) && from.isBefore(booking.getCheckOutDate()), "Stay history is outside contracted dates");
            if (index == 0) {
                require("CHECK_IN".equals(segment.getAssignmentType()) && from.equals(booking.getCheckInDate())
                        && segment.getRoomTypeId().equals(booking.getRoomTypeId()), "Initial stay does not match reservation");
            } else {
                BookingRoomAssignment old = segments.get(index - 1);
                require("ROOM_CHANGE".equals(segment.getAssignmentType()) && segment.getStartTime().equals(old.getEndTime()), "Stay history has a gap or overlap");
            }
            boolean last = index == segments.size() - 1;
            require(last ? segment.getEndTime() == null && segment.getRoomId().equals(booking.getAssignedRoomId())
                    : segment.getEndTime() != null && !segment.getEndTime().isBefore(segment.getStartTime()), "Active stay history is inconsistent");
            Map<LocalDate, BigDecimal> current = new HashMap<>();
            for (LocalDate date = from; date.isBefore(booking.getCheckOutDate()); date = date.plusDays(1)) {
                LocalDate night = date;
                List<FolioItem> charges = items.stream().filter(i -> "ROOM_CHARGE".equals(i.getItemType())
                        && segment.getId().equals(i.getRoomAssignmentId()) && night.equals(i.getBusinessDate())).toList();
                require(charges.size() == 1, "Missing or duplicate nightly room charge");
                FolioItem charge = charges.get(0);
                require(charge.getRoomId().equals(segment.getRoomId()) && charge.getRoomTypeId().equals(segment.getRoomTypeId())
                        && charge.getSourceItemId() == null && charge.getAmount().signum() > 0
                        && charge.getQuantity().compareTo(BigDecimal.ONE) == 0 && charge.getUnitPrice().compareTo(charge.getAmount()) == 0, "Nightly charge attribution or amount is invalid");
                if (index == 0 || segment.getRoomTypeId().equals(segments.get(index - 1).getRoomTypeId()))
                    require(previous.containsKey(date) && previous.get(date).compareTo(charge.getAmount()) == 0, "Locked nightly price was changed");
                current.put(date, charge.getAmount()); consumed.add(charge.getId());
                List<FolioItem> credits = items.stream().filter(i -> charge.getId().equals(i.getSourceItemId())).toList();
                boolean reversed = !last && !date.isBefore(segment.getEndTime().toLocalDate());
                require(credits.size() == (reversed ? 1 : 0), "Room charge reversal is missing or unexpected");
                if (reversed) {
                    FolioItem credit = credits.get(0);
                    require("ROOM_RATE_ADJUSTMENT".equals(credit.getItemType()) && credit.getAmount().negate().compareTo(charge.getAmount()) == 0
                            && Objects.equals(credit.getRoomAssignmentId(), charge.getRoomAssignmentId()) && Objects.equals(credit.getBusinessDate(), charge.getBusinessDate())
                            && Objects.equals(credit.getRoomId(), charge.getRoomId()) && Objects.equals(credit.getRoomTypeId(), charge.getRoomTypeId())
                            && credit.getQuantity().compareTo(BigDecimal.ONE) == 0 && credit.getUnitPrice().compareTo(credit.getAmount()) == 0, "Room reversal does not match original charge");
                    consumed.add(credit.getId());
                }
            }
            if (index > 0) {
                BookingRoomAssignment old = segments.get(index - 1);
                List<RoomBillingEvent> matches = events.stream().filter(e -> e.getOldAssignmentId().equals(old.getId()) && e.getNewAssignmentId().equals(segment.getId()) && e.getChangeDate().equals(from)).toList();
                require(matches.size() == 1, "Room change has no completed billing event");
                RoomBillingEvent event = matches.get(0);
                require(event.getBookingId().equals(booking.getId()) && event.getNewChargesTotal().compareTo(current.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)) == 0,
                        "Room change posted total does not match billing event");
                eventIds.add(event.getId());
            }
            previous = current;
        }
        require(eventIds.size() == events.size(), "Unmatched room change event");
        require(consumed.size() == items.size(), "Unmatched fees or unsupported extra-consumption confirmation; checkout blocked");
    }
}
