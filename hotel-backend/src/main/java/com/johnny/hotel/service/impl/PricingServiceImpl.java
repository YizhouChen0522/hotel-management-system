package com.johnny.hotel.service.impl;

import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.dto.pricing.NightlyRate;
import com.johnny.hotel.dto.pricing.RoomPriceQuote;
import com.johnny.hotel.entity.RoomRate;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.mapper.RoomRateMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final RoomTypeMapper roomTypeMapper;
    private final RoomRateMapper roomRateMapper;

    @Override
    public RoomPriceQuote quoteRoomType(
            Long roomTypeId,
            LocalDate checkInDate,
            LocalDate checkOutDate) {

        validateDateRange(checkInDate, checkOutDate);

        RoomType roomType =
                roomTypeMapper.selectById(roomTypeId);

        if (roomType == null) {
            throw new BusinessException(
                    "Room type does not exist"
            );
        }

        if (roomType.getStatus() != 1) {
            throw new BusinessException(
                    "Room type is disabled"
            );
        }

        List<RoomRate> specialRates =
                roomRateMapper.selectByRoomTypeIdAndDateRange(
                        roomTypeId,
                        checkInDate,
                        checkOutDate
                );

        Map<LocalDate, RoomRate> specialRateMap =
                new HashMap<>();

        for (RoomRate rate : specialRates) {
            specialRateMap.put(
                    rate.getRateDate(),
                    rate
            );
        }

        List<NightlyRate> nightlyRates =
                new ArrayList<>();

        BigDecimal totalPrice =
                BigDecimal.ZERO;

        LocalDate currentDate =
                checkInDate;

        while (currentDate.isBefore(checkOutDate)) {

            RoomRate specialRate =
                    specialRateMap.get(currentDate);

            BigDecimal price;
            String rateSource;

            if (specialRate != null) {

                price = specialRate.getPrice();
                rateSource = specialRate.getRateSource();

            } else {

                price = roomType.getBasePrice();
                rateSource = "BASE";
            }

            nightlyRates.add(
                    new NightlyRate(
                            currentDate,
                            roomTypeId,
                            price,
                            rateSource
                    )
            );

            totalPrice =
                    totalPrice.add(price);

            currentDate =
                    currentDate.plusDays(1);
        }

        int nights =
                Math.toIntExact(
                        ChronoUnit.DAYS.between(
                                checkInDate,
                                checkOutDate
                        )
                );

        return new RoomPriceQuote(
                roomTypeId,
                checkInDate,
                checkOutDate,
                nights,
                nightlyRates,
                totalPrice
        );
    }

    private void validateDateRange(
            LocalDate checkInDate,
            LocalDate checkOutDate) {

        if (checkInDate == null
                || checkOutDate == null) {

            throw new BusinessException(
                    "Check-in date and check-out date are required"
            );
        }

        if (!checkOutDate.isAfter(checkInDate)) {

            throw new BusinessException(
                    "Check-out date must be after check-in date"
            );
        }
    }
}
