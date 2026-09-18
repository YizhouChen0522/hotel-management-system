package com.johnny.hotel.walkin;

import com.johnny.hotel.guest.GuestViews;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class WalkInViews {
    private WalkInViews() {}
    @Builder public record Created(Long bookingId, GuestViews.Profile booker, GuestViews.Profile primaryGuest,
        Integer reservationStatus, String reservationSource, LocalDate checkInDate, LocalDate checkOutDate,
        Long roomTypeId, Long reservedRoomId, BigDecimal totalPrice, String currency, boolean registrationReady) {}
}
