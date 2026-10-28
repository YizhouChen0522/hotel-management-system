package com.johnny.hotel.stay;

import com.johnny.hotel.pagination.*;
import com.johnny.hotel.entity.StayRoomAssignment;
import com.johnny.hotel.mapper.StayRoomAssignmentMapper;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.RoomMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.enums.RoomStatus;
import com.johnny.hotel.enums.RoomTypeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StayQueryService {
    private final StayAccess access;
    private final StayMapper stays;
    private final StayRoomAssignmentMapper assignments;
    private final BookingMapper bookings;
    private final RoomMapper rooms;
    private final RoomTypeMapper roomTypes;
    private final StayAdjustmentMapper adjustments;
    private final RoomConflictReader conflicts;
    private final Clock clock;
    private final PaginationSupport pagination;

    public record RoomChangeCandidate(
            Long id,
            String roomNumber,
            Long roomTypeId,
            String roomTypeName,
            Integer floor,
            Integer status
    ) {}

    public record RoomChangeOptions(
            LocalDate remainingFrom,
            LocalDate effectiveCheckOutDate,
            Long currentRoomId,
            List<RoomChangeCandidate> rooms
    ) {}
    public Stay get(Long id){return access.read(id);}
    public PageResult<Stay> page(Long bookingId,Integer status,Integer page,Integer pageSize) {
        var owner=access.customerScope();if(status!=null)try{StayStatus.fromCode(status);}catch(IllegalArgumentException e){throw new com.johnny.hotel.exception.BusinessException("Invalid stay status");}
        var window=pagination.window(page,pageSize,bookingId!=null || status!=null);
        int size=pagination.limit(window);
        return pagination.result(window,size==0?List.of():stays.page(owner,bookingId,status,window.offset(),size),stays.count(owner,bookingId,status));
    }
    public List<StayRoomAssignment> assignments(Long id){access.read(id);return assignments.readByStayId(id);}

    @Transactional(readOnly = true)
    public RoomChangeOptions roomChangeOptions(Long id) {
        var stay = access.read(id);
        if (stay.getStatus() != StayStatus.IN_HOUSE.getCode()) {
            throw new com.johnny.hotel.exception.BusinessException("Room change requires an in-house Stay");
        }
        var booking = bookings.selectById(stay.getBookingId());
        if (booking == null) {
            throw new com.johnny.hotel.exception.BusinessException(404, "Booking not found");
        }
        var current = conflicts.activeAssignment(id);
        if (current == null) {
            throw new com.johnny.hotel.exception.BusinessException("Stay has no current assignment");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate end = StayPlan.end(booking, adjustments.readByStayId(id));
        LocalDate conflictEnd = end.isAfter(today) ? end : end.plusDays(1);
        var candidates = roomTypes.selectAll().stream()
                .filter(type -> type.getStatus() == RoomTypeStatus.ENABLED.getCode())
                .filter(type -> booking.getGuestCount() <= type.getCapacity())
                .flatMap(type -> rooms.sellableCandidates(type.getId()).stream()
                        .filter(room -> !room.getId().equals(current.getRoomId()))
                        .filter(room -> room.getStatus() == RoomStatus.AVAILABLE.getCode()
                                || room.getStatus() == RoomStatus.BOOKED.getCode())
                        .filter(room -> conflicts.overlapping(
                                room.getId(),
                                booking.getId(),
                                today,
                                conflictEnd
                        ).isEmpty())
                        .map(room -> new RoomChangeCandidate(
                                room.getId(),
                                room.getRoomNumber(),
                                room.getRoomTypeId(),
                                type.getTypeName(),
                                room.getFloor(),
                                room.getStatus()
                        )))
                .toList();
        return new RoomChangeOptions(today, end, current.getRoomId(), candidates);
    }
}
