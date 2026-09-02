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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingPricingServiceImpl implements BookingPricingService {

    private final BookingMapper bookingMapper;

    private final BookingPriceVersionMapper bookingPriceVersionMapper;

    private final BookingNightlyRateMapper bookingNightlyRateMapper;

    private final PricingService pricingService;

    @Override
    @Transactional
    public BookingPriceVersion createPriceVersion(
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

        BookingPriceVersion version =
                new BookingPriceVersion();

        version.setBookingId(bookingId);
        version.setVersionNo(nextVersionNo);
        version.setChangeType(changeType);
        version.setReason(normalizeReason(reason));
        version.setIsActive(1);
        version.setTotalPrice(quote.getTotalPrice());
        version.setCurrency("CAD");
        version.setCreatedBy(operatorId);

        int versionInserted =
                bookingPriceVersionMapper.insert(version);

        if (versionInserted != 1
                || version.getId() == null) {

            throw new BusinessException(
                    "Failed to create booking price version"
            );
        }

        for (NightlyRate nightlyRate : quote.getNightlyRates()) {

            BookingNightlyRate snapshot =
                    new BookingNightlyRate();

            snapshot.setPriceVersionId(version.getId());
            snapshot.setBookingId(bookingId);
            snapshot.setStayDate(nightlyRate.getStayDate());
            snapshot.setRoomTypeId(nightlyRate.getRoomTypeId());
            snapshot.setRateAmount(nightlyRate.getPrice());
            snapshot.setRateSource(nightlyRate.getRateSource());

            int inserted =
                    bookingNightlyRateMapper.insert(snapshot);

            if (inserted != 1) {
                throw new BusinessException(
                        "Failed to save booking nightly rate"
                );
            }
        }

        /*
         * booking.total_price 继续保留，
         * 但现在它只是 active price version 的汇总快照。
         */
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

    private String normalizeReason(String reason) {

        if (reason == null || reason.isBlank()) {
            return null;
        }

        return reason.trim();
    }
}
