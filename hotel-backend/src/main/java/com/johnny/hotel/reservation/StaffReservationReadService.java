package com.johnny.hotel.reservation;

import com.johnny.hotel.entity.BookingNightlyRate;
import com.johnny.hotel.entity.BookingPriceVersion;
import com.johnny.hotel.entity.Room;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.BookingNightlyRateMapper;
import com.johnny.hotel.mapper.BookingPriceVersionMapper;
import com.johnny.hotel.mapper.RoomMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.stay.RoomConflictReader;
import com.johnny.hotel.vo.RoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffReservationReadService {
    private final BookingMapper bookings;
    private final RoomMapper rooms;
    private final RoomTypeMapper roomTypes;
    private final RoomConflictReader conflicts;
    private final BookingPriceVersionMapper priceVersions;
    private final BookingNightlyRateMapper nightlyRates;

    public record PriceSnapshot(BookingPriceVersion version, List<BookingNightlyRate> nightlyRates) {}

    @Transactional(readOnly = true)
    public List<RoomVO> eligibleRooms(Long bookingId) {
        var booking = bookings.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException(404, "Booking not found");
        }
        return rooms.sellableCandidates(booking.getRoomTypeId()).stream()
                .filter(room -> conflicts.overlapping(
                        room.getId(),
                        bookingId,
                        booking.getCheckInDate(),
                        booking.getCheckOutDate()
                ).isEmpty())
                .map(this::toRoomView)
                .toList();
    }

    @Transactional(readOnly = true)
    public PriceSnapshot priceSnapshot(Long bookingId) {
        if (bookings.selectById(bookingId) == null) {
            throw new BusinessException(404, "Booking not found");
        }
        return new PriceSnapshot(
                priceVersions.selectActiveReadByBookingId(bookingId),
                nightlyRates.selectActiveReadByBookingId(bookingId)
        );
    }

    private RoomVO toRoomView(Room room) {
        var roomType = roomTypes.selectById(room.getRoomTypeId());
        return RoomVO.builder()
                .id(room.getId())
                .roomNumber(room.getRoomNumber())
                .roomTypeId(room.getRoomTypeId())
                .roomTypeName(roomType == null ? null : roomType.getTypeName())
                .floor(room.getFloor())
                .status(room.getStatus())
                .createTime(room.getCreateTime())
                .updateTime(room.getUpdateTime())
                .build();
    }
}
