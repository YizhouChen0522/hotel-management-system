package com.johnny.hotel.service.impl;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.Booking;
import com.johnny.hotel.entity.Room;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.RoomMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.vo.BookingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingMapper bookingMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final RoomMapper roomMapper;
    private final SysAuditLogMapper sysAuditLogMapper;

    private BookingVO toVO(com.johnny.hotel.entity.Booking booking) {
        RoomType roomType = roomTypeMapper.selectById(booking.getRoomTypeId());

        Room assignedRoom = null;
        if (booking.getAssignedRoomId() != null) {
            assignedRoom = roomMapper.selectById(booking.getAssignedRoomId());
        }

        return BookingVO.builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .roomTypeId(booking.getRoomTypeId())
                .roomTypeName(roomType == null ? null : roomType.getTypeName())
                .assignedRoomId(booking.getAssignedRoomId())
                .assignedRoomNumber(assignedRoom == null ? null : assignedRoom.getRoomNumber())
                .guestCount(booking.getGuestCount())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .createTime(booking.getCreateTime())
                .updateTime(booking.getUpdateTime())
                .build();
    }
    private BookingVO getBookingByIdInternal(Long id) {
        Booking booking = bookingMapper.selectById(id);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        return toVO(booking);
    }
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("Start date and end date are required");
        }

        if (endDate.isBefore(startDate)) {
            throw new BusinessException("End date cannot be before start date");
        }
    }
    private Map<Long, Room> lockRoomsInOrder(Long roomId1, Long roomId2) {

        long firstId = Math.min(roomId1, roomId2);
        long secondId = Math.max(roomId1, roomId2);

        Map<Long, Room> lockedRooms = new HashMap<>();

        Room firstRoom = roomMapper.selectByIdForUpdate(firstId);

        if (firstRoom != null) {
            lockedRooms.put(firstId, firstRoom);
        }

        if (firstId != secondId) {
            Room secondRoom = roomMapper.selectByIdForUpdate(secondId);

            if (secondRoom != null) {
                lockedRooms.put(secondId, secondRoom);
            }
        }

        return lockedRooms;
    }
    private String normalizeReason(String reason) {

        if (reason == null || reason.isBlank()) {
            return "Not provided";
        }

        return reason.trim();
    }

    @Override
    public List<BookingVO> getMyBookings(Long currentUserId) {
        return bookingMapper.selectByUserId(currentUserId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<BookingVO> getBookingsPage(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 50 : size;

        if (safeSize > 100) {
            safeSize = 100;
        }

        int offset = (safePage - 1) * safeSize;

        return bookingMapper.selectPage(offset, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<BookingVO> getPendingBookingsPage(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 50 : size;

        if (safeSize > 100) {
            safeSize = 100;
        }

        int offset = (safePage - 1) * safeSize;

        return bookingMapper.selectPendingPage(offset, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public BookingVO createBooking(CreateBookingRequest request, Long currentUserId) {
        RoomType roomType = roomTypeMapper.selectById(request.getRoomTypeId());

        if (roomType == null) {
            throw new BusinessException("Room type does not exist");
        }

        if (roomType.getStatus() != 1) {
            throw new BusinessException("Room type is not available");
        }

        if (request.getCheckOutDate().isBefore(request.getCheckInDate())
                || request.getCheckOutDate().isEqual(request.getCheckInDate())) {
            throw new BusinessException("Check-out date must be after check-in date");
        }

        if (request.getGuestCount() > roomType.getCapacity()) {
            throw new BusinessException("Guest count exceeds room type capacity");
        }

        long nights = ChronoUnit.DAYS.between(
                request.getCheckInDate(),
                request.getCheckOutDate()
        );

        BigDecimal totalPrice = roomType.getBasePrice()
                .multiply(BigDecimal.valueOf(nights));

        Booking booking = Booking.builder()
                .userId(currentUserId)
                .roomTypeId(request.getRoomTypeId())
                .assignedRoomId(null)
                .guestCount(request.getGuestCount())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .status(0)
                .totalPrice(totalPrice)
                .build();

        bookingMapper.insert(booking);

        return getBookingByIdInternal(booking.getId());
    }

    @Override
    @Transactional
    public BookingVO approveBooking(Long bookingId,
                                    ApproveBookingRequest request,
                                    Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 0) {
            throw new BusinessException(
                    "Only pending bookings can be approved"
            );
        }

        Room room =
                roomMapper.selectByIdForUpdate(
                        request.getAssignedRoomId()
                );

        if (room == null) {
            throw new BusinessException("Room does not exist");
        }

        if (room.getStatus() != 1) {
            throw new BusinessException(
                    "Room is not available"
            );
        }

        if (!room.getRoomTypeId()
                .equals(booking.getRoomTypeId())) {

            throw new BusinessException(
                    "Room type does not match booking room type"
            );
        }

        int bookingUpdated =
                bookingMapper.approveBooking(
                        bookingId,
                        room.getId(),
                        1
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to approve booking"
            );
        }

        int roomUpdated =
                roomMapper.updateStatus(
                        room.getId(),
                        2
                );

        if (roomUpdated != 1) {
            throw new BusinessException(
                    "Failed to update room status"
            );
        }

        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(currentUserId)
                        .targetUserId(booking.getUserId())
                        .action("APPROVE_BOOKING")
                        .detail(
                                "Booking id: "
                                        + bookingId
                                        + ", assigned room id: "
                                        + room.getId()
                        )
                        .build()
        );

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO rejectBooking(Long bookingId, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 0) {
            throw new BusinessException("Only pending bookings can be rejected");
        }

        bookingMapper.updateStatus(bookingId, 5);

        sysAuditLogMapper.insert(SysAuditLog.builder()
                .operatorId(currentUserId)
                .targetUserId(booking.getUserId())
                .action("REJECT_BOOKING")
                .detail("Rejected booking id: " + bookingId)
                .build());

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO checkIn(Long bookingId, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 1) {
            throw new BusinessException("Only approved bookings can be checked in");
        }

        if (booking.getAssignedRoomId() == null) {
            throw new BusinessException("No room has been assigned to this booking");
        }

        Room room = roomMapper.selectById(booking.getAssignedRoomId());

        if (room == null) {
            throw new BusinessException("Assigned room does not exist");
        }

        if (room.getStatus() != 2) {
            throw new BusinessException("Assigned room is not booked");
        }

        bookingMapper.updateStatus(bookingId, 2);
        roomMapper.updateStatus(room.getId(), 4);

        sysAuditLogMapper.insert(SysAuditLog.builder()
                .operatorId(currentUserId)
                .targetUserId(booking.getUserId())
                .action("CHECK_IN")
                .detail("Checked in booking id: " + bookingId
                        + ", room id: " + room.getId())
                .build());

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO checkOut(Long bookingId, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 2) {
            throw new BusinessException("Only checked-in bookings can be checked out");
        }

        if (booking.getAssignedRoomId() == null) {
            throw new BusinessException("No room has been assigned to this booking");
        }

        bookingMapper.updateStatus(bookingId, 3);
        roomMapper.updateStatus(booking.getAssignedRoomId(), 3);

        sysAuditLogMapper.insert(SysAuditLog.builder()
                .operatorId(currentUserId)
                .targetUserId(booking.getUserId())
                .action("CHECK_OUT")
                .detail("Checked out booking id: " + bookingId
                        + ", room id: " + booking.getAssignedRoomId())
                .build());

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO cancelBooking(Long bookingId, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException("You can only cancel your own booking");
        }

        if (booking.getStatus() == 2 || booking.getStatus() == 3) {
            throw new BusinessException("Checked-in or checked-out bookings cannot be cancelled");
        }

        bookingMapper.updateStatus(bookingId, 4);

        if (booking.getAssignedRoomId() != null) {
            Room room = roomMapper.selectById(booking.getAssignedRoomId());

            if (room != null && room.getStatus() == 2) {
                roomMapper.updateStatus(room.getId(), 1);
            }
        }

        sysAuditLogMapper.insert(SysAuditLog.builder()
                .operatorId(currentUserId)
                .targetUserId(booking.getUserId())
                .action("CANCEL_BOOKING")
                .detail("Cancelled booking id: " + bookingId)
                .build());

        return getBookingByIdInternal(bookingId);
    }
    @Override
    public List<BookingVO> getBookingsByStatus(Integer status, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 50 : size;

        if (safeSize > 100) {
            safeSize = 100;
        }

        int offset = (safePage - 1) * safeSize;

        return bookingMapper.selectPageByStatus(status, offset, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<BookingVO> getBookingsByUserId(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 50 : size;

        if (safeSize > 100) {
            safeSize = 100;
        }

        int offset = (safePage - 1) * safeSize;

        return bookingMapper.selectPageByUserId(userId, offset, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }
    @Override
    public List<BookingVO> getBookingsByCheckInDateRange(LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
        validateDateRange(startDate, endDate);

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 50 : size;

        if (safeSize > 100) {
            safeSize = 100;
        }

        int offset = (safePage - 1) * safeSize;

        return bookingMapper.selectPageByCheckInDateRange(startDate, endDate, offset, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<BookingVO> getBookingsByCheckOutDateRange(LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
        validateDateRange(startDate, endDate);

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 50 : size;

        if (safeSize > 100) {
            safeSize = 100;
        }

        int offset = (safePage - 1) * safeSize;

        return bookingMapper.selectPageByCheckOutDateRange(startDate, endDate, offset, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public BookingVO getMyBookingById(Long bookingId, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException("You can only view your own booking");
        }

        return toVO(booking);
    }

    @Override
    public BookingVO getBookingByIdForAdmin(Long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        return toVO(booking);
    }

    @Override
    @Transactional
    public BookingVO updateBooking(Long bookingId,
                                   UpdateBookingRequest request,
                                   Long currentUserId) {

        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException("You can only update your own booking");
        }

        if (booking.getStatus() != 0) {
            throw new BusinessException("Only pending bookings can be updated");
        }

        RoomType roomType = roomTypeMapper.selectById(request.getRoomTypeId());

        if (roomType == null) {
            throw new BusinessException("Room type does not exist");
        }

        if (roomType.getStatus() != 1) {
            throw new BusinessException("Room type is not available");
        }

        if (request.getCheckOutDate().isBefore(request.getCheckInDate())
                || request.getCheckOutDate().isEqual(request.getCheckInDate())) {
            throw new BusinessException("Check-out date must be after check-in date");
        }

        if (request.getGuestCount() > roomType.getCapacity()) {
            throw new BusinessException("Guest count exceeds room type capacity");
        }

        long nights = ChronoUnit.DAYS.between(
                request.getCheckInDate(),
                request.getCheckOutDate()
        );

        BigDecimal totalPrice = roomType.getBasePrice()
                .multiply(BigDecimal.valueOf(nights));

        Booking updatedBooking = Booking.builder()
                .id(bookingId)
                .roomTypeId(request.getRoomTypeId())
                .guestCount(request.getGuestCount())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalPrice(totalPrice)
                .build();

        bookingMapper.updatePendingBooking(updatedBooking);

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO cancelBookingByAdmin(Long bookingId, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() == 2) {
            throw new BusinessException("Checked-in bookings cannot be cancelled");
        }

        if (booking.getStatus() == 3) {
            throw new BusinessException("Checked-out bookings cannot be cancelled");
        }

        if (booking.getStatus() == 4) {
            throw new BusinessException("Booking is already cancelled");
        }

        if (booking.getStatus() == 5) {
            throw new BusinessException("Rejected bookings cannot be cancelled");
        }

        if (booking.getAssignedRoomId() != null) {
            Room room = roomMapper.selectById(booking.getAssignedRoomId());

            if (room != null && room.getStatus() == 2) {
                roomMapper.updateStatus(room.getId(), 1);
            }
        }

        bookingMapper.updateStatus(bookingId, 4);

        sysAuditLogMapper.insert(SysAuditLog.builder()
                .operatorId(currentUserId)
                .targetUserId(booking.getUserId())
                .action("ADMIN_CANCEL_BOOKING")
                .detail("Admin cancelled booking id: " + bookingId)
                .build());

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO reassignRoom(Long bookingId,
                                  ReassignRoomRequest request,
                                  Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 1) {
            throw new BusinessException(
                    "Only approved bookings can have their room reassigned"
            );
        }

        Long oldRoomId = booking.getAssignedRoomId();
        Long newRoomId = request.getNewRoomId();

        if (oldRoomId == null) {
            throw new BusinessException(
                    "Booking does not currently have an assigned room"
            );
        }

        if (oldRoomId.equals(newRoomId)) {
            throw new BusinessException(
                    "New room is the same as the currently assigned room"
            );
        }

        Map<Long, Room> lockedRooms =
                lockRoomsInOrder(oldRoomId, newRoomId);

        Room oldRoom = lockedRooms.get(oldRoomId);
        Room newRoom = lockedRooms.get(newRoomId);

        if (oldRoom == null) {
            throw new BusinessException(
                    "Currently assigned room does not exist"
            );
        }

        if (newRoom == null) {
            throw new BusinessException(
                    "New room does not exist"
            );
        }

        if (oldRoom.getStatus() != 2) {
            throw new BusinessException(
                    "Currently assigned room is not in booked status"
            );
        }


        if (newRoom.getStatus() != 1) {
            throw new BusinessException(
                    "New room is not available"
            );
        }

        if (!newRoom.getRoomTypeId()
                .equals(booking.getRoomTypeId())) {

            throw new BusinessException(
                    "New room type must match booking room type"
            );
        }

        int oldRoomUpdated =
                roomMapper.updateStatus(oldRoomId, 1);

        if (oldRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to release previously assigned room"
            );
        }

        int newRoomUpdated =
                roomMapper.updateStatus(newRoomId, 2);

        if (newRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to reserve new room"
            );
        }

        int bookingUpdated =
                bookingMapper.updateAssignedRoom(
                        bookingId,
                        newRoomId
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update assigned room"
            );
        }

        String reason = normalizeReason(request.getReason());

        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(currentUserId)
                        .targetUserId(booking.getUserId())
                        .action("REASSIGN_BOOKING_ROOM")
                        .detail(
                                "Booking id: " + bookingId
                                        + ", old room id: " + oldRoomId
                                        + ", new room id: " + newRoomId
                                        + ", reason: " + reason
                        )
                        .build()
        );

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO changeRoomType(Long bookingId,
                                    ChangeBookingRoomTypeRequest request,
                                    Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 1) {
            throw new BusinessException(
                    "Only approved bookings can change room type"
            );
        }

        if (booking.getAssignedRoomId() == null) {
            throw new BusinessException(
                    "Booking does not currently have an assigned room"
            );
        }

        Long oldRoomId = booking.getAssignedRoomId();
        Long newRoomId = request.getNewRoomId();

        if (oldRoomId.equals(newRoomId)) {
            throw new BusinessException(
                    "New room must be different from the currently assigned room"
            );
        }

        RoomType newRoomType =
                roomTypeMapper.selectById(
                        request.getNewRoomTypeId()
                );

        if (newRoomType == null) {
            throw new BusinessException(
                    "New room type does not exist"
            );
        }

        if (newRoomType.getStatus() != 1) {
            throw new BusinessException(
                    "New room type is disabled"
            );
        }

        if (newRoomType.getId()
                .equals(booking.getRoomTypeId())) {

            throw new BusinessException(
                    "New room type is the same as the current room type; use room reassignment instead"
            );
        }

        if (booking.getGuestCount()
                > newRoomType.getCapacity()) {

            throw new BusinessException(
                    "Guest count exceeds new room type capacity"
            );
        }

        Map<Long, Room> lockedRooms =
                lockRoomsInOrder(oldRoomId, newRoomId);

        Room oldRoom =
                lockedRooms.get(oldRoomId);

        Room newRoom =
                lockedRooms.get(newRoomId);

        if (oldRoom == null) {
            throw new BusinessException(
                    "Currently assigned room does not exist"
            );
        }

        if (newRoom == null) {
            throw new BusinessException(
                    "New room does not exist"
            );
        }

        if (oldRoom.getStatus() != 2) {
            throw new BusinessException(
                    "Currently assigned room is not in booked status"
            );
        }

        if (newRoom.getStatus() != 1) {
            throw new BusinessException(
                    "New room is not available"
            );
        }

        if (!newRoom.getRoomTypeId()
                .equals(newRoomType.getId())) {

            throw new BusinessException(
                    "New room does not belong to the selected room type"
            );
        }

        long nights =
                ChronoUnit.DAYS.between(
                        booking.getCheckInDate(),
                        booking.getCheckOutDate()
                );

        if (nights <= 0) {
            throw new BusinessException(
                    "Invalid booking date range"
            );
        }

        BigDecimal oldTotalPrice =
                booking.getTotalPrice();

        BigDecimal newTotalPrice =
                newRoomType.getBasePrice()
                        .multiply(
                                BigDecimal.valueOf(nights)
                        );

        int oldRoomUpdated =
                roomMapper.updateStatus(
                        oldRoomId,
                        1
                );

        if (oldRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to release previously assigned room"
            );
        }

        int newRoomUpdated =
                roomMapper.updateStatus(
                        newRoomId,
                        2
                );

        if (newRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to reserve new room"
            );
        }

        int bookingUpdated =
                bookingMapper.updateRoomTypeAndAssignedRoom(
                        bookingId,
                        newRoomType.getId(),
                        newRoomId,
                        newTotalPrice
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update booking room type"
            );
        }

        String reason =
                normalizeReason(request.getReason());

        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(currentUserId)
                        .targetUserId(booking.getUserId())
                        .action("CHANGE_BOOKING_ROOM_TYPE")
                        .detail(
                                "Booking id: " + bookingId
                                        + ", old room type id: "
                                        + booking.getRoomTypeId()
                                        + ", new room type id: "
                                        + newRoomType.getId()
                                        + ", old room id: "
                                        + oldRoomId
                                        + ", new room id: "
                                        + newRoomId
                                        + ", old total price: "
                                        + oldTotalPrice
                                        + ", new total price: "
                                        + newTotalPrice
                                        + ", reason: "
                                        + reason
                        )
                        .build()
        );

        return getBookingByIdInternal(bookingId);
    }

}
