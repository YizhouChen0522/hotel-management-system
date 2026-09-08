package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.dto.RoomChangeBillingCommand;
import com.johnny.hotel.dto.pricing.NightlyRate;
import com.johnny.hotel.dto.pricing.RoomPriceQuote;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import java.util.Objects;
import static com.johnny.hotel.service.support.BillingRules.*;
import com.johnny.hotel.entity.Folio;
import com.johnny.hotel.entity.FolioItem;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.FolioItemMapper;
import com.johnny.hotel.mapper.FolioMapper;
import com.johnny.hotel.service.FolioFinancialService;
import com.johnny.hotel.service.FolioService;
import com.johnny.hotel.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FolioServiceImpl implements FolioService {

    private final BookingRoomAssignmentMapper assignments;
    private final RoomBillingEventMapper events;
    private final BookingPriceVersionMapper versions;
    private final com.johnny.hotel.service.support.PriceSnapshotValidator snapshots;
    private final BookingMapper bookingMapper;

    private final FolioMapper folioMapper;

    private final FolioItemMapper folioItemMapper;

    private final PricingService pricingService;

    private final FolioFinancialService folioFinancialService;

    @Value("${hotel.currency}")
    private String hotelCurrency;

    @Override
    @Transactional
    public Folio ensureFolioExists(
            Long bookingId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        Folio existing = folioMapper.selectByBookingIdForUpdate(bookingId);

        if (existing != null) {
            return existing;
        }

        require(booking.getStatus() == 0, "Missing folio cannot be reconstructed for an existing stay");
        BookingPriceVersion version = versions.selectActiveByBookingId(bookingId);
        require(version != null, "Price snapshot must exist before account creation");
        snapshots.validate(booking, version.getCurrency());
        Folio folio = Folio.builder()
                .bookingId(bookingId)
                .status("OPEN")
                .currency(version.getCurrency())
                .totalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .balanceAmount(BigDecimal.ZERO)
                .build();
        int inserted =
                folioMapper.insert(folio);

        if (inserted != 1 || folio.getId() == null) {

            throw new BusinessException(
                    "Failed to create folio"
            );
        }

        return folio;
    }

    @Override
    @Transactional
    public FolioItem addItem(
            Long bookingId,
            FolioItemCommand command,
            Long operatorId) {

        List<FolioItem> items =
                addItems(
                        bookingId,
                        List.of(command),
                        operatorId
                );

        return items.get(0);
    }
    @Override
    @Transactional
    public List<FolioItem> addItems(Long bookingId, List<FolioItemCommand> commands, Long operatorId) {
        require(commands != null && !commands.isEmpty(), "Folio items cannot be empty");
        Booking booking = bookingMapper.selectByIdForUpdate(bookingId);
        require(booking != null, "Booking does not exist");
        // Assignment reads precede the Folio lock. Every writer of these rows owns Booking first.
        List<BookingRoomAssignment> segments = assignments.selectByBookingId(bookingId);
        Folio folio = folioMapper.selectByBookingIdForUpdate(bookingId);
        require(folio != null, "Folio does not exist");
        require(folio.getClosedTime() == null && !"VOID".equals(folio.getStatus()) && booking.getStatus() != 3,
                "Cannot add fees to a finalized or void account");
        List<FolioItem> ledger = new ArrayList<>(folioItemMapper.selectByFolioIdForUpdate(folio.getId()));
        List<FolioItem> result = new ArrayList<>();
        for (FolioItemCommand command : commands) {
            item(command);
            boolean roomCharge = "ROOM_CHARGE".equals(command.getItemType());
            boolean reversal = "ROOM_RATE_ADJUSTMENT".equals(command.getItemType());
            if (roomCharge || reversal) {
                require(booking.getStatus() == 2, "Room charges require a checked-in booking");
                require(command.getQuantity().compareTo(BigDecimal.ONE) == 0, "Room charge quantity must be one");
                require(command.getRoomAssignmentId() != null, "Room charges require an assignment");
            }
            if (command.getRoomAssignmentId() != null) {
                BookingRoomAssignment segment = segments.stream().filter(a -> a.getId().equals(command.getRoomAssignmentId())).findFirst().orElse(null);
                require(segment != null && Objects.equals(segment.getRoomId(), command.getRoomId())
                        && Objects.equals(segment.getRoomTypeId(), command.getRoomTypeId()), "Assignment, booking, room or room type mismatch");
                require(!command.getBusinessDate().isBefore(segment.getStartTime().toLocalDate())
                        && command.getBusinessDate().isBefore(booking.getCheckOutDate()), "Charge date is outside the assignment contract");
            } else require(command.getRoomId() == null && command.getRoomTypeId() == null, "Room attribution requires an assignment");
            String key = roomCharge ? "ROOM:" + command.getRoomAssignmentId() + ":" + command.getBusinessDate()
                    : reversal ? "REV:" + command.getSourceItemId() : command.getEventKey();
            require(key != null && !key.isBlank(), "A stable billing event key is required");
            FolioItem candidate = FolioItem.builder().folioId(folio.getId()).eventKey(key)
                    .itemType(command.getItemType()).description(command.getDescription().trim()).businessDate(command.getBusinessDate())
                    .quantity(command.getQuantity()).unitPrice(command.getUnitPrice()).amount(command.getAmount())
                    .roomId(command.getRoomId()).roomTypeId(command.getRoomTypeId()).roomAssignmentId(command.getRoomAssignmentId())
                    .sourceItemId(command.getSourceItemId()).refundable(Boolean.TRUE.equals(command.getRefundable()) ? 1 : 0).createdBy(operatorId).build();
            FolioItem existing = ledger.stream().filter(i -> key.equalsIgnoreCase(i.getEventKey())).findFirst().orElse(null);
            if (existing != null) {
                require(sameItem(existing, candidate), "Billing event key was already used with different content");
                result.add(existing);
                continue;
            }
            if (candidate.getSourceItemId() != null) {
                FolioItem source = ledger.stream().filter(i -> i.getId().equals(candidate.getSourceItemId())).findFirst().orElse(null);
                require(source != null && source.getAmount().signum() > 0, "Source charge must belong to this folio");
                require(Objects.equals(source.getBusinessDate(), candidate.getBusinessDate())
                        && Objects.equals(source.getRoomAssignmentId(), candidate.getRoomAssignmentId())
                        && Objects.equals(source.getRoomId(), candidate.getRoomId()) && Objects.equals(source.getRoomTypeId(), candidate.getRoomTypeId()), "Credit attribution must match its source");
                BigDecimal credits = ledger.stream().filter(i -> source.getId().equals(i.getSourceItemId())).map(FolioItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                require(source.getAmount().add(credits).add(candidate.getAmount()).signum() >= 0, "Credit exceeds remaining source amount");
                if (reversal) require("ROOM_CHARGE".equals(source.getItemType()) && candidate.getAmount().negate().compareTo(source.getAmount()) == 0 && credits.signum() == 0,
                        "Room reversal must reverse an untouched complete nightly charge");
                else require(!"ROOM_CHARGE".equals(source.getItemType()), "Room rate discounts require a separate policy");
            }
            one(folioItemMapper.insert(candidate));
            require(candidate.getId() != null, "Folio item insert did not return an id");
            ledger.add(candidate); result.add(candidate);
        }
        folioFinancialService.recalculateSummary(bookingId);
        return result;
    }

    @Override
    @Transactional
    public void applyRoomChangeBilling(RoomChangeBillingCommand command) {
        require(command != null, "Room change command is required");
        Booking booking = bookingMapper.selectByIdForUpdate(command.getBookingId());

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        List<BookingRoomAssignment> history = assignments.selectByBookingId(booking.getId());
        BookingRoomAssignment old = history.stream().filter(a -> a.getId().equals(command.getOldAssignmentId())).findFirst().orElse(null);
        BookingRoomAssignment next = history.stream().filter(a -> a.getId().equals(command.getNewAssignmentId())).findFirst().orElse(null);
        require(old != null && next != null && old.getBookingId().equals(booking.getId()) && next.getBookingId().equals(booking.getId()), "Room change assignments must belong to this booking");
        int oldIndex = -1, nextIndex = -1;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId().equals(old.getId())) oldIndex = i;
            if (history.get(i).getId().equals(next.getId())) nextIndex = i;
        }
        require(oldIndex >= 0 && nextIndex == oldIndex + 1, "Room change must connect adjacent stay segments");
        require(Objects.equals(old.getRoomId(), command.getOldRoomId()) && Objects.equals(next.getRoomId(), command.getNewRoomId())
                && Objects.equals(old.getRoomTypeId(), command.getOldRoomTypeId()) && Objects.equals(next.getRoomTypeId(), command.getNewRoomTypeId())
                && old.getEndTime() != null && old.getEndTime().equals(next.getStartTime()) && "ROOM_CHANGE".equals(next.getAssignmentType())
                && next.getStartTime().toLocalDate().equals(command.getChangeDate()) && booking.getCheckOutDate().equals(command.getCheckOutDate()), "Room change command does not match actual stay history");
        List<RoomBillingEvent> completed = events.selectByBookingIdForUpdate(booking.getId());
        RoomBillingEvent previous = completed.stream().filter(e -> e.getOldAssignmentId().equals(old.getId()) || e.getNewAssignmentId().equals(next.getId())).findFirst().orElse(null);
        if (previous != null) {
            require(previous.getOldAssignmentId().equals(old.getId()) && previous.getNewAssignmentId().equals(next.getId()) && previous.getChangeDate().equals(command.getChangeDate()), "Room change event conflicts with history");
            return;
        }
        require(booking.getStatus() == 2 && next.getEndTime() == null && next.getRoomId().equals(booking.getAssignedRoomId()), "Room change requires the current active stay");
        require(!command.getChangeDate().isBefore(booking.getCheckInDate()) && command.getChangeDate().isBefore(booking.getCheckOutDate()), "Room change is outside contracted dates");
        Folio folio = folioMapper.selectByBookingIdForUpdate(booking.getId());
        require(folio != null && folio.getClosedTime() == null && !"VOID".equals(folio.getStatus()), "Account is missing or finalized");
        snapshots.validate(booking, folio.getCurrency());
        List<FolioItem> oldFutureCharges = folioItemMapper.selectByFolioIdForUpdate(folio.getId()).stream()
                .filter(i -> "ROOM_CHARGE".equals(i.getItemType()) && old.getId().equals(i.getRoomAssignmentId()) && !i.getBusinessDate().isBefore(command.getChangeDate())).toList();
        require(oldFutureCharges.size() == java.time.temporal.ChronoUnit.DAYS.between(command.getChangeDate(), booking.getCheckOutDate())
                && oldFutureCharges.stream().map(FolioItem::getBusinessDate).distinct().count() == oldFutureCharges.size(), "Remaining nightly charges are incomplete");
        if (oldFutureCharges.isEmpty()) {
            throw new BusinessException(
                    "Remaining room charges for current room assignment do not exist"
            );
        }

        List<FolioItemCommand> ledgerCommands =
                new ArrayList<>();

        for (FolioItem oldCharge : oldFutureCharges) {

            BigDecimal reversedAmount =
                    oldCharge.getAmount().negate();

            ledgerCommands.add(
                    FolioItemCommand.builder()
                            .itemType("ROOM_RATE_ADJUSTMENT")
                            .description(
                                    "Reversal of room charge for "
                                            + oldCharge.getBusinessDate()
                                            + " due to room change"
                            )
                            .businessDate(oldCharge.getBusinessDate())
                            .quantity(BigDecimal.ONE)
                            .unitPrice(reversedAmount)
                            .amount(reversedAmount)
                            .roomId(command.getOldRoomId())
                            .roomTypeId(command.getOldRoomTypeId())
                            .roomAssignmentId(command.getOldAssignmentId())
                            .sourceItemId(oldCharge.getId())
                            .refundable(true)
                            .build()
            );
        }


        boolean roomTypeChanged =
                !command.getOldRoomTypeId().equals(command.getNewRoomTypeId());

        if (!roomTypeChanged) {
            for (FolioItem oldCharge : oldFutureCharges) {

                ledgerCommands.add(
                        FolioItemCommand.builder()
                                .itemType("ROOM_CHARGE")
                                .description(
                                        "Room charge for "
                                                + oldCharge.getBusinessDate()
                                                + " after room change"
                                )
                                .businessDate(oldCharge.getBusinessDate())
                                .quantity(BigDecimal.ONE)
                                .unitPrice(oldCharge.getAmount())
                                .amount(oldCharge.getAmount())
                                .roomId(command.getNewRoomId())
                                .roomTypeId(command.getNewRoomTypeId())
                                .roomAssignmentId(command.getNewAssignmentId())
                                .sourceItemId(null)
                                .refundable(true)
                                .build()
                );
            }

        } else {

            RoomPriceQuote newQuote =
                    pricingService.quoteRoomType(
                            command.getNewRoomTypeId(),
                            command.getChangeDate(),
                            command.getCheckOutDate()
                    );

            if (newQuote.getNightlyRates() == null
                    || newQuote.getNightlyRates().isEmpty()) {

                throw new BusinessException(
                        "New room type pricing does not exist"
                );
            }

            for (NightlyRate nightlyRate
                    : newQuote.getNightlyRates()) {

                ledgerCommands.add(
                        FolioItemCommand.builder()
                                .itemType("ROOM_CHARGE")
                                .description(
                                        "Room charge for "
                                                + nightlyRate.getStayDate()
                                                + " after room type change"
                                )
                                .businessDate(nightlyRate.getStayDate())
                                .quantity(BigDecimal.ONE)
                                .unitPrice(nightlyRate.getPrice())
                                .amount(nightlyRate.getPrice())
                                .roomId(command.getNewRoomId())
                                .roomTypeId(command.getNewRoomTypeId())
                                .roomAssignmentId(command.getNewAssignmentId())
                                .sourceItemId(null)
                                .refundable(true)
                                .build()
                );
            }
        }

        var charges = ledgerCommands.stream().filter(c -> "ROOM_CHARGE".equals(c.getItemType())).toList();
        require(charges.size() == java.time.temporal.ChronoUnit.DAYS.between(command.getChangeDate(), command.getCheckOutDate())
                && charges.stream().map(FolioItemCommand::getBusinessDate).distinct().count() == charges.size(), "New nightly quote is incomplete");
        addItems(
                command.getBookingId(),
                ledgerCommands,
                command.getOperatorId()
        );
        BigDecimal total = ledgerCommands.stream().filter(c -> "ROOM_CHARGE".equals(c.getItemType())).map(FolioItemCommand::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        one(events.insert(RoomBillingEvent.builder().bookingId(booking.getId()).oldAssignmentId(old.getId()).newAssignmentId(next.getId())
                .changeDate(command.getChangeDate()).newChargesTotal(total).build()));
    }

    private boolean sameItem(FolioItem a, FolioItem b) {
        return Objects.equals(a.getItemType(), b.getItemType()) && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getBusinessDate(), b.getBusinessDate()) && a.getAmount().compareTo(b.getAmount()) == 0
                && a.getQuantity().compareTo(b.getQuantity()) == 0 && a.getUnitPrice().compareTo(b.getUnitPrice()) == 0
                && Objects.equals(a.getRoomAssignmentId(), b.getRoomAssignmentId()) && Objects.equals(a.getRoomId(), b.getRoomId())
                && Objects.equals(a.getRoomTypeId(), b.getRoomTypeId()) && Objects.equals(a.getSourceItemId(), b.getSourceItemId())
                && Objects.equals(a.getRefundable(), b.getRefundable());
    }
}
