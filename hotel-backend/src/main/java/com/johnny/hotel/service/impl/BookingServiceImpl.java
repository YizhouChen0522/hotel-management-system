package com.johnny.hotel.service.impl;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.dto.CreateBookingRequest;
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
import java.util.List;


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
    private BookingVO getBookingById(Long id) {
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

        return getBookingById(booking.getId());
    }
    @Override
    @Transactional
    public BookingVO approveBooking(Long bookingId, ApproveBookingRequest request, Long currentUserId) {
        Booking booking = bookingMapper.selectById(bookingId);

        if (booking == null) {
            throw new BusinessException("Booking does not exist");
        }

        if (booking.getStatus() != 0) {
            throw new BusinessException("Only pending bookings can be approved");
        }

        Room room = roomMapper.selectById(request.getAssignedRoomId());

        if (room == null) {
            throw new BusinessException("Assigned room does not exist");
        }

        if (room.getStatus() != 1) {
            throw new BusinessException("Only available rooms can be assigned");
        }

        if (!room.getRoomTypeId().equals(booking.getRoomTypeId())) {
            throw new BusinessException("Assigned room type does not match booking room type");
        }

        bookingMapper.approveBooking(bookingId, request.getAssignedRoomId(), 1);
        roomMapper.updateStatus(request.getAssignedRoomId(), 2);

        sysAuditLogMapper.insert(SysAuditLog.builder()
                .operatorId(currentUserId)
                .targetUserId(booking.getUserId())
                .action("APPROVE_BOOKING")
                .detail("Approved booking id: " + bookingId
                        + ", assigned room id: " + request.getAssignedRoomId())
                .build());

        return getBookingById(bookingId);
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

        return getBookingById(bookingId);
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

        return getBookingById(bookingId);
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

        return getBookingById(bookingId);
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

        return getBookingById(bookingId);
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


}
