package com.johnny.hotel.service.impl;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.service.FolioService;
import com.johnny.hotel.vo.BookingVO;
import com.johnny.hotel.service.BookingPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final BookingRoomAssignmentMapper bookingRoomAssignmentMapper;
    private final BookingPriceVersionMapper bookingPriceVersionMapper;
    private final BookingNightlyRateMapper bookingNightlyRateMapper;
    private final FolioService folioService;
    private final BookingPricingService bookingPricingService;

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
    public BookingVO createBooking(CreateBookingRequest request,
                                   Long currentUserId) {

        RoomType roomType =
                roomTypeMapper.selectById(request.getRoomTypeId());

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

        if (request.getGuestCount() > roomType.getCapacity()) {
            throw new BusinessException(
                    "Guest count exceeds room type capacity"
            );
        }

        if (!request.getCheckOutDate()
                .isAfter(request.getCheckInDate())) {

            throw new BusinessException(
                    "Check-out date must be after check-in date"
            );
        }

        Booking booking = new Booking();

        booking.setUserId(currentUserId);
        booking.setRoomTypeId(request.getRoomTypeId());
        booking.setAssignedRoomId(null);
        booking.setGuestCount(request.getGuestCount());
        booking.setCheckInDate(request.getCheckInDate());
        booking.setCheckOutDate(request.getCheckOutDate());

        booking.setStatus(0);


        booking.setTotalPrice(BigDecimal.ZERO);

        int inserted =
                bookingMapper.insert(booking);

        if (inserted != 1
                || booking.getId() == null) {

            throw new BusinessException(
                    "Failed to create booking"
            );
        }

        bookingPricingService.createFullRepriceVersion(
                booking.getId(),
                booking.getRoomTypeId(),
                "ORIGINAL_BOOKING",
                "Initial booking price",
                currentUserId
        );

        return getBookingByIdInternal(
                booking.getId()
        );
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
    public BookingVO checkIn(
            Long bookingId,
            Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        /*
         * 2. 只有 APPROVED Booking 才能 check-in。
         *
         * Booking status:
         * 0 PENDING
         * 1 APPROVED
         * 2 CHECKED_IN
         * 3 CHECKED_OUT
         * 4 CANCELLED
         * 5 REJECTED
         */
        if (booking.getStatus() != 1) {
            throw new BusinessException(
                    "Only approved bookings can be checked in"
            );
        }

        if (booking.getAssignedRoomId() == null) {
            throw new BusinessException(
                    "Booking does not have an assigned room"
            );
        }

        BookingPriceVersion activePriceVersion =
                bookingPriceVersionMapper
                        .selectActiveByBookingId(bookingId);

        if (activePriceVersion == null) {
            throw new BusinessException(
                    "Booking price snapshot does not exist"
            );
        }

        BookingRoomAssignment existingAssignment =
                bookingRoomAssignmentMapper
                        .selectActiveByBookingId(bookingId);

        if (existingAssignment != null) {
            throw new BusinessException(
                    "Booking already has an active room assignment"
            );
        }

        Long roomId =
                booking.getAssignedRoomId();

        Room room =
                roomMapper.selectByIdForUpdate(roomId);

        if (room == null) {
            throw new BusinessException(
                    "Assigned room does not exist"
            );
        }

        if (room.getStatus() != 2) {
            throw new BusinessException(
                    "Assigned room is not in booked status"
            );
        }

        if (!room.getRoomTypeId()
                .equals(booking.getRoomTypeId())) {

            throw new BusinessException(
                    "Assigned room type does not match booking room type"
            );
        }

        LocalDateTime checkInTime =
                LocalDateTime.now();


        int bookingUpdated =
                bookingMapper.updateStatus(
                        bookingId,
                        2
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update booking check-in status"
            );
        }

        int roomUpdated =
                roomMapper.updateStatus(
                        roomId,
                        4
                );

        if (roomUpdated != 1) {
            throw new BusinessException(
                    "Failed to update room occupancy status"
            );
        }

        BookingRoomAssignment assignment = BookingRoomAssignment.builder()
                .bookingId(bookingId)
                .roomId(roomId)
                .roomTypeId(room.getRoomTypeId())
                .assignmentType("CHECK_IN")
                .startTime(checkInTime)
                .changeReason("Initial check-in")
                .createdBy(currentUserId)
                .build();

        int assignmentInserted =
                bookingRoomAssignmentMapper
                        .insert(assignment);

        if (assignmentInserted != 1
                || assignment.getId() == null) {

            throw new BusinessException(
                    "Failed to create room assignment"
            );
        }

        List<BookingNightlyRate> nightlyRates =
                bookingNightlyRateMapper
                        .selectActiveByBookingId(
                                bookingId
                        );

        if (nightlyRates == null
                || nightlyRates.isEmpty()) {

            throw new BusinessException(
                    "Booking nightly price snapshot does not exist"
            );
        }


        List<FolioItemCommand> roomChargeCommands =
                new ArrayList<>();

        for (BookingNightlyRate nightlyRate
                : nightlyRates) {

            FolioItemCommand command = FolioItemCommand.builder()
                    .itemType("ROOM_CHARGE")
                    .description("Room charge for " + nightlyRate.getStayDate())
                    .businessDate(nightlyRate.getStayDate())
                    .quantity(BigDecimal.ONE)
                    .unitPrice(nightlyRate.getRateAmount())
                    .amount(nightlyRate.getRateAmount())
                    .roomId(roomId)
                    .roomTypeId(nightlyRate.getRoomTypeId())
                    .roomAssignmentId(assignment.getId())
                    .sourceItemId(null)
                    .refundable(true)
                    .build();

            roomChargeCommands.add(command);
        }
        folioService.addItems(
                bookingId,
                roomChargeCommands,
                currentUserId
        );

        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(currentUserId)
                        .targetUserId(booking.getUserId())
                        .action("CHECK_IN")
                        .detail(
                                "Booking " + bookingId
                                        + " checked in to roomId "
                                        + roomId
                                        + ", assignmentId "
                                        + assignment.getId()
                        )
                        .build()
        );
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
    public BookingVO updateBooking(
            Long bookingId,
            UpdateBookingRequest request,
            Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException(
                    "You can only update your own booking"
            );
        }

        if (booking.getStatus() != 0) {
            throw new BusinessException(
                    "Only pending bookings can be updated"
            );
        }

        if (request.getCheckInDate() == null
                || request.getCheckOutDate() == null) {

            throw new BusinessException(
                    "Check-in date and check-out date are required"
            );
        }

        if (!request.getCheckOutDate()
                .isAfter(request.getCheckInDate())) {

            throw new BusinessException(
                    "Check-out date must be after check-in date"
            );
        }

        if (request.getCheckInDate()
                .isBefore(LocalDate.now())) {

            throw new BusinessException(
                    "Check-in date cannot be in the past"
            );
        }

        RoomType roomType =
                roomTypeMapper.selectById(
                        request.getRoomTypeId()
                );

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

        if (request.getGuestCount() == null
                || request.getGuestCount() < 1) {

            throw new BusinessException(
                    "Guest count must be at least 1"
            );
        }

        if (request.getGuestCount()
                > roomType.getCapacity()) {

            throw new BusinessException(
                    "Guest count exceeds room type capacity"
            );
        }

        boolean roomTypeChanged =
                !booking.getRoomTypeId()
                        .equals(request.getRoomTypeId());

        boolean checkInDateChanged =
                !booking.getCheckInDate()
                        .equals(request.getCheckInDate());

        boolean checkOutDateChanged =
                !booking.getCheckOutDate()
                        .equals(request.getCheckOutDate());

        boolean dateChanged =
                checkInDateChanged
                        || checkOutDateChanged;

        boolean guestCountChanged =
                !booking.getGuestCount()
                        .equals(request.getGuestCount());

        if (!roomTypeChanged
                && !dateChanged
                && !guestCountChanged) {

            return getBookingByIdInternal(
                    bookingId
            );
        }

        int updated =
                bookingMapper.updatePendingBookingDetails(
                        bookingId,
                        request.getRoomTypeId(),
                        request.getGuestCount(),
                        request.getCheckInDate(),
                        request.getCheckOutDate()
                );

        if (updated != 1) {
            throw new BusinessException(
                    "Failed to update booking"
            );
        }

        if (roomTypeChanged) {

            bookingPricingService
                    .createFullRepriceVersion(
                            bookingId,
                            request.getRoomTypeId(),
                            "PRE_CHECKIN_ROOM_TYPE_CHANGE",
                            "Customer changed room type before check-in",
                            currentUserId
                    );

        } else if (dateChanged) {

            bookingPricingService
                    .createDateChangeVersion(
                            bookingId,
                            "Customer changed booking dates",
                            currentUserId
                    );
        }

        return getBookingByIdInternal(
                bookingId
        );
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
    public BookingVO changeRoomType(
            Long bookingId,
            ChangeBookingRoomTypeRequest request,
            Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
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

        Long oldRoomId =
                booking.getAssignedRoomId();

        Long newRoomId =
                request.getNewRoomId();

        Long oldRoomTypeId =
                booking.getRoomTypeId();

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
                .equals(oldRoomTypeId)) {

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
                lockRoomsInOrder(
                        oldRoomId,
                        newRoomId
                );

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


        BigDecimal oldTotalPrice =
                booking.getTotalPrice();


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
                        newRoomId
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update booking room type"
            );
        }

        BookingPriceVersion newPriceVersion =
                bookingPricingService.createFullRepriceVersion(
                        bookingId,
                        newRoomType.getId(),
                        "PRE_CHECKIN_ROOM_TYPE_CHANGE",
                        normalizeReason(request.getReason()),
                        currentUserId
                );

        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(currentUserId)
                        .targetUserId(booking.getUserId())
                        .action("CHANGE_BOOKING_ROOM_TYPE")
                        .detail(
                                "Booking " + bookingId
                                        + ": room type "
                                        + oldRoomTypeId
                                        + " -> "
                                        + newRoomType.getId()
                                        + ", room "
                                        + oldRoomId
                                        + " -> "
                                        + newRoomId
                                        + ", price "
                                        + oldTotalPrice
                                        + " -> "
                                        + newPriceVersion.getTotalPrice()
                        )
                        .build()
        );

        return getBookingByIdInternal(
                bookingId
        );
    }

    @Override
    @Transactional
    public BookingVO changeRoomDuringStay(
            Long bookingId,
            ChangeRoomDuringStayRequest request,
            Long currentUserId) {

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        if (booking.getStatus() != 2) {
            throw new BusinessException(
                    "Only checked-in bookings can change room during stay"
            );
        }

        if (booking.getAssignedRoomId() == null) {
            throw new BusinessException(
                    "Booking does not have a current assigned room"
            );
        }

        Long oldRoomId = booking.getAssignedRoomId();

        Long newRoomId = request.getNewRoomId();

        if (oldRoomId.equals(newRoomId)) {
            throw new BusinessException(
                    "New room must be different from the current room"
            );
        }

        BookingRoomAssignment currentAssignment =
                bookingRoomAssignmentMapper
                        .selectActiveByBookingId(bookingId);

        if (currentAssignment == null) {
            throw new BusinessException(
                    "Active room assignment does not exist"
            );
        }

        /*
         * Booking 当前房间和住宿历史当前房间
         * 必须保持一致。
         * 如果不一致，说明数据库状态已经损坏，
         * 不应该继续自动修。
         */
        if (!currentAssignment.getRoomId()
                .equals(oldRoomId)) {

            throw new BusinessException(
                    "Current room assignment does not match booking assigned room"
            );
        }

        Map<Long, Room> lockedRooms =
                lockRoomsInOrder(
                        oldRoomId,
                        newRoomId
                );

        Room oldRoom =
                lockedRooms.get(oldRoomId);

        Room newRoom =
                lockedRooms.get(newRoomId);

        if (oldRoom == null) {
            throw new BusinessException(
                    "Current room does not exist"
            );
        }

        if (newRoom == null) {
            throw new BusinessException(
                    "New room does not exist"
            );
        }
        if (!currentAssignment.getRoomTypeId().equals(oldRoom.getRoomTypeId())) {
            throw new BusinessException(
                    "Current room assignment type does not match current room type"
            );
        }

        if (oldRoom.getStatus() != 4) {
            throw new BusinessException(
                    "Current room is not occupied"
            );
        }

        if (newRoom.getStatus() != 1) {
            throw new BusinessException(
                    "New room is not available"
            );
        }

        RoomType newRoomType =
                roomTypeMapper.selectById(
                        newRoom.getRoomTypeId()
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

        if (booking.getGuestCount()
                > newRoomType.getCapacity()) {

            throw new BusinessException(
                    "Guest count exceeds new room type capacity"
            );
        }

        LocalDateTime changeTime =
                LocalDateTime.now();

        int assignmentClosed = bookingRoomAssignmentMapper.closeAssignment(
                                currentAssignment.getId(),
                                changeTime
                        );

        if (assignmentClosed != 1) {
            throw new BusinessException(
                    "Failed to close current room assignment"
            );
        }

        /*
         * 6. 客人已经真实住过旧房。
         * 所以不能：
         * OCCUPIED -> AVAILABLE
         * 必须：
         * OCCUPIED -> MAINTENANCE
         * 等清洁完成后再由员工：
         * MAINTENANCE -> AVAILABLE
         */
        int oldRoomUpdated =
                roomMapper.updateStatus(
                        oldRoomId,
                        3
                );

        if (oldRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to release current room for maintenance"
            );
        }

        int newRoomUpdated = roomMapper.updateStatus(
                        newRoomId,
                        4
                );

        if (newRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to occupy new room"
            );
        }

        int bookingUpdated =
                bookingMapper.updateAssignedRoom(
                        bookingId,
                        newRoomId
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update booking assigned room"
            );
        }

        BookingRoomAssignment newAssignment = BookingRoomAssignment.builder()
                .bookingId(bookingId)
                .roomId(newRoomId)
                .roomTypeId(newRoom.getRoomTypeId())
                .assignmentType("ROOM_CHANGE")
                .startTime(changeTime)
                .endTime(null)
                .changeReason(normalizeReason(request.getReason()))
                .createdBy(currentUserId)
                .build();

        int assignmentInserted = bookingRoomAssignmentMapper.insert(newAssignment);

        if (assignmentInserted != 1
                || newAssignment.getId() == null) {

            throw new BusinessException(
                    "Failed to create new room assignment"
            );
        }
        folioService.applyRoomChangeBilling(
                RoomChangeBillingCommand.builder()
                        .bookingId(bookingId)
                        .oldAssignmentId(currentAssignment.getId())
                        .newAssignmentId(newAssignment.getId())
                        .oldRoomId(oldRoomId)
                        .newRoomId(newRoomId)
                        .oldRoomTypeId(currentAssignment.getRoomTypeId())
                        .newRoomTypeId(newRoom.getRoomTypeId())
                        .changeDate(changeTime.toLocalDate())
                        .checkOutDate(booking.getCheckOutDate())
                        .reason(normalizeReason(request.getReason()))
                        .operatorId(currentUserId)
                        .build()
        );

        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(currentUserId)
                        .targetUserId(booking.getUserId())
                        .action("CHANGE_ROOM_DURING_STAY")
                        .detail(
                                "Booking "
                                        + bookingId
                                        + ": roomId "
                                        + oldRoomId
                                        + " -> "
                                        + newRoomId
                                        + ", roomTypeId "
                                        + oldRoom.getRoomTypeId()
                                        + " -> "
                                        + newRoom.getRoomTypeId()
                                        + ", assignmentId "
                                        + currentAssignment.getId()
                                        + " -> "
                                        + newAssignment.getId()
                                        + ", reason: "
                                        + normalizeReason(
                                        request.getReason()
                                )
                        )
                        .build()
        );

        return getBookingByIdInternal(
                bookingId
        );
    }

}
