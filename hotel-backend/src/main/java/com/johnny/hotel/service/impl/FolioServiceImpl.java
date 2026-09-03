package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.dto.RoomChangeBillingCommand;
import com.johnny.hotel.dto.pricing.NightlyRate;
import com.johnny.hotel.dto.pricing.RoomPriceQuote;
import com.johnny.hotel.entity.Booking;
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

    private final BookingMapper bookingMapper;

    private final FolioMapper folioMapper;

    private final FolioItemMapper folioItemMapper;

    private final PricingService pricingService;

    private final FolioFinancialService folioFinancialService;

    @Value("${hotel.currency}")
    private String hotelCurrency;

    private void validateItemCommand(
            FolioItemCommand command) {

        if (command == null) {
            throw new BusinessException(
                    "Folio item cannot be null"
            );
        }

        if (command.getItemType() == null || command.getItemType().isBlank()) {

            throw new BusinessException(
                    "Folio item type is required"
            );
        }

        if (command.getDescription() == null || command.getDescription().isBlank()) {

            throw new BusinessException(
                    "Folio item description is required"
            );
        }

        if (command.getDescription().length() > 500) {

            throw new BusinessException(
                    "Folio item description cannot exceed 500 characters"
            );
        }

        if (command.getAmount() == null) {
            throw new BusinessException(
                    "Folio item amount is required"
            );
        }

        if (command.getAmount()
                .compareTo(BigDecimal.ZERO) == 0) {

            throw new BusinessException(
                    "Folio item amount cannot be zero"
            );
        }

        if (command.getQuantity() != null
                && command.getQuantity()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(
                    "Quantity must be greater than zero"
            );
        }
    }


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

        Folio existing = folioMapper.selectByBookingId(bookingId);

        if (existing != null) {
            return existing;
        }

        Folio folio = Folio.builder()
                .bookingId(bookingId)
                .status("OPEN")
                .currency(hotelCurrency)
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
    public List<FolioItem> addItems(
            Long bookingId,
            List<FolioItemCommand> commands,
            Long operatorId) {

        if (commands == null
                || commands.isEmpty()) {

            throw new BusinessException(
                    "Folio items cannot be empty"
            );
        }

        for (FolioItemCommand command : commands) {
            validateItemCommand(command);
        }

        ensureFolioExists(bookingId);


        Folio folio =
                folioMapper
                        .selectByBookingIdForUpdate(
                                bookingId
                        );

        if (folio == null) {
            throw new BusinessException(
                    "Folio does not exist"
            );
        }

        if ("VOID".equals(folio.getStatus())) {
            throw new BusinessException(
                    "Cannot add items to a void folio"
            );
        }

        List<FolioItem> createdItems =
                new ArrayList<>();

        for (FolioItemCommand command : commands) {

            FolioItem item = FolioItem.builder()
                    .folioId(folio.getId())
                    .itemType(command.getItemType())
                    .description(command.getDescription().trim())
                    .businessDate(command.getBusinessDate())
                    .quantity(command.getQuantity())
                    .unitPrice(command.getUnitPrice())
                    .amount(command.getAmount())
                    .roomId(command.getRoomId())
                    .roomTypeId(command.getRoomTypeId())
                    .roomAssignmentId(command.getRoomAssignmentId())
                    .sourceItemId(command.getSourceItemId())
                    .refundable(Boolean.TRUE.equals(command.getRefundable()) ? 1 : 0)
                    .createdBy(operatorId)
                    .build();

            int inserted =
                    folioItemMapper.insert(item);

            if (inserted != 1
                    || item.getId() == null) {

                throw new BusinessException(
                        "Failed to create folio item"
                );
            }

            createdItems.add(item);
        }

        folioFinancialService.recalculateSummary(bookingId);

        return createdItems;
    }

    @Override
    @Transactional
    public void applyRoomChangeBilling(RoomChangeBillingCommand command) {

        Folio folio =
                folioMapper.selectByBookingIdForUpdate(
                        command.getBookingId()
                );

        if (folio == null) {
            throw new BusinessException(
                    "Folio does not exist for checked-in booking"
            );
        }

        if ("VOID".equals(folio.getStatus())) {
            throw new BusinessException(
                    "Cannot modify a void folio"
            );
        }

        if (!command.getChangeDate()
                .isBefore(command.getCheckOutDate())) {

            return;
        }

        List<FolioItem> oldFutureCharges =
                folioItemMapper.selectRoomChargesFromDate(
                        folio.getId(),
                        command.getOldAssignmentId(),
                        command.getChangeDate()
                );

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

        addItems(
                command.getBookingId(),
                ledgerCommands,
                command.getOperatorId()
        );
    }
}