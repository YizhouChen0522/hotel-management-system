package com.johnny.hotel.service.impl;

import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.dto.pricing.NightlyRate;
import com.johnny.hotel.dto.pricing.RoomPriceQuote;
import com.johnny.hotel.entity.Booking;
import com.johnny.hotel.entity.BookingNightlyRate;
import com.johnny.hotel.entity.BookingPriceVersion;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.BookingNightlyRateMapper;
import com.johnny.hotel.mapper.BookingPriceVersionMapper;
import com.johnny.hotel.service.BookingPricingService;
import com.johnny.hotel.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingPricingServiceImpl implements BookingPricingService {

    private final BookingMapper bookingMapper;

    private final BookingPriceVersionMapper bookingPriceVersionMapper;

    private final BookingNightlyRateMapper bookingNightlyRateMapper;

    private final PricingService pricingService;

    @Value("${hotel.currency}")
    private String hotelCurrency;

    @Override
    @Transactional
    public BookingPriceVersion createFullRepriceVersion(
            Long bookingId,
            Long roomTypeId,
            String changeType,
            String reason,
            Long operatorId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        RoomPriceQuote quote =
                pricingService.quoteRoomType(
                        roomTypeId,
                        booking.getCheckInDate(),
                        booking.getCheckOutDate()
                );

        Integer maxVersionNo =
                bookingPriceVersionMapper
                        .selectMaxVersionNo(bookingId);

        int nextVersionNo =
                (maxVersionNo == null ? 0 : maxVersionNo) + 1;

        bookingPriceVersionMapper
                .deactivateActiveVersion(bookingId);

        BookingPriceVersion version = BookingPriceVersion.builder()
                .bookingId(bookingId)
                .versionNo(nextVersionNo)
                .changeType(changeType)
                .reason(normalizeReason(reason))
                .isActive(1)
                .totalPrice(quote.getTotalPrice())
                .currency(hotelCurrency)
                .createdBy(operatorId)
                .build();

        int versionInserted =
                bookingPriceVersionMapper.insert(version);

        if (versionInserted != 1
                || version.getId() == null) {

            throw new BusinessException(
                    "Failed to create booking price version"
            );
        }

        for (NightlyRate nightlyRate : quote.getNightlyRates()) {

            BookingNightlyRate snapshot = BookingNightlyRate.builder()
                    .priceVersionId(version.getId())
                    .bookingId(bookingId)
                    .stayDate(nightlyRate.getStayDate())
                    .roomTypeId(nightlyRate.getRoomTypeId())
                    .rateAmount(nightlyRate.getPrice())
                    .rateSource(nightlyRate.getRateSource())
                    .build();

            int inserted =
                    bookingNightlyRateMapper.insert(snapshot);

            if (inserted != 1) {
                throw new BusinessException(
                        "Failed to save booking nightly rate"
                );
            }
        }

        int bookingUpdated =
                bookingMapper.updateTotalPrice(
                        bookingId,
                        quote.getTotalPrice()
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update booking total price"
            );
        }

        return version;
    }
    @Override
    @Transactional
    public BookingPriceVersion createDateChangeVersion(
            Long bookingId,
            String reason,
            Long operatorId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        BookingPriceVersion oldVersion =
                bookingPriceVersionMapper
                        .selectActiveByBookingId(bookingId);

        if (oldVersion == null) {
            throw new BusinessException(
                    "Active booking price version does not exist"
            );
        }

        List<BookingNightlyRate> oldRates =
                bookingNightlyRateMapper
                        .selectActiveByBookingId(bookingId);

        Map<LocalDate, BookingNightlyRate> oldRateMap =
                new HashMap<>();

        for (BookingNightlyRate rate : oldRates) {
            oldRateMap.put(
                    rate.getStayDate(),
                    rate
            );
        }

        RoomPriceQuote currentQuote =
                pricingService.quoteRoomType(
                        booking.getRoomTypeId(),
                        booking.getCheckInDate(),
                        booking.getCheckOutDate()
                );

        Map<LocalDate, NightlyRate> currentRateMap =
                new HashMap<>();

        for (NightlyRate rate :
                currentQuote.getNightlyRates()) {

            currentRateMap.put(
                    rate.getStayDate(),
                    rate
            );
        }

        Integer maxVersionNo =
                bookingPriceVersionMapper
                        .selectMaxVersionNo(bookingId);

        int nextVersionNo =
                (maxVersionNo == null ? 0 : maxVersionNo) + 1;

        List<BookingNightlyRate> newSnapshots =
                new ArrayList<>();

        BigDecimal newTotal =
                BigDecimal.ZERO;

        LocalDate currentDate =
                booking.getCheckInDate();

        while (currentDate.isBefore(
                booking.getCheckOutDate())) {

            BookingNightlyRate oldRate = oldRateMap.get(currentDate);

            BookingNightlyRate snapshot = BookingNightlyRate.builder()
                    .bookingId(bookingId)
                    .stayDate(currentDate)
                    .roomTypeId(booking.getRoomTypeId())
                    .build();

            if (oldRate != null) {

                snapshot.setRateAmount(
                        oldRate.getRateAmount()
                );

                snapshot.setRateSource(
                        oldRate.getRateSource()
                );

            } else {

                NightlyRate currentRate =
                        currentRateMap.get(currentDate);

                if (currentRate == null) {
                    throw new BusinessException(
                            "Unable to determine nightly rate for "
                                    + currentDate
                    );
                }

                snapshot.setRateAmount(
                        currentRate.getPrice()
                );

                snapshot.setRateSource(
                        currentRate.getRateSource()
                );
            }

            newTotal =
                    newTotal.add(
                            snapshot.getRateAmount()
                    );

            newSnapshots.add(snapshot);

            currentDate =
                    currentDate.plusDays(1);
        }

        bookingPriceVersionMapper
                .deactivateActiveVersion(bookingId);

        BookingPriceVersion newVersion = BookingPriceVersion.builder()
                .bookingId(bookingId)
                .versionNo(nextVersionNo)
                .changeType("PRE_CHECKIN_DATE_CHANGE")
                .reason(normalizeReason(reason))
                .isActive(1)
                .totalPrice(newTotal)
                .currency(oldVersion.getCurrency())
                .createdBy(operatorId)
                .build();

        int versionInserted =
                bookingPriceVersionMapper
                        .insert(newVersion);

        if (versionInserted != 1
                || newVersion.getId() == null) {

            throw new BusinessException(
                    "Failed to create booking price version"
            );
        }

        for (BookingNightlyRate snapshot :
                newSnapshots) {

            snapshot.setPriceVersionId(
                    newVersion.getId()
            );

            int inserted =
                    bookingNightlyRateMapper
                            .insert(snapshot);

            if (inserted != 1) {
                throw new BusinessException(
                        "Failed to save booking nightly rate"
                );
            }
        }

        int updated =
                bookingMapper.updateTotalPrice(
                        bookingId,
                        newTotal
                );

        if (updated != 1) {
            throw new BusinessException(
                    "Failed to update booking total price"
            );
        }

        return newVersion;
    }

    private String normalizeReason(String reason) {

        if (reason == null || reason.isBlank()) {
            return null;
        }

        return reason.trim();
    }
}
