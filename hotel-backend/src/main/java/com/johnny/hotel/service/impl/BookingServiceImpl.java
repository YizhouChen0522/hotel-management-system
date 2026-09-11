package com.johnny.hotel.service.impl;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.enums.BookingStatus;
import com.johnny.hotel.enums.RoomStatus;
import com.johnny.hotel.enums.RoomTypeStatus;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.service.FolioService;
import com.johnny.hotel.service.StayHistoryService;
import com.johnny.hotel.vo.BookingVO;
import com.johnny.hotel.service.BookingPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static com.johnny.hotel.service.support.BillingRules.*;
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
    private final java.time.Clock clock;
    private final com.johnny.hotel.stay.StayPlan stayPlan;
    private final com.johnny.hotel.stay.RoomConflictReader roomConflicts;
    private final FolioMapper folioMapper;
    private final com.johnny.hotel.service.support.PriceSnapshotValidator snapshotValidator;
    private final com.johnny.hotel.service.support.CheckoutFinalizer checkoutFinalizer;
    private final BookingMapper bookingMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final RoomMapper roomMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final BookingRoomAssignmentMapper bookingRoomAssignmentMapper;
    private final BookingPriceVersionMapper bookingPriceVersionMapper;
    private final BookingNightlyRateMapper bookingNightlyRateMapper;
    private final FolioService folioService;
    private final BookingPricingService bookingPricingService;
    private final StayHistoryService stayHistoryService;
    private final com.johnny.hotel.service.RoomTurnoverTaskService turnoverTasks;

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
        Booking booking = bookingMapper.selectByIdForUpdate(id);

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

        dates(request.getCheckInDate(), request.getCheckOutDate());
        require(!request.getCheckInDate().isBefore(LocalDate.now(clock)), "Historical bookings are not supported");
        require(request.getGuestCount() != null && request.getGuestCount() > 0, "Guest count must be positive");
        RoomType roomType =
                roomTypeMapper.selectById(request.getRoomTypeId());

        if (roomType == null) {
            throw new BusinessException(
                    "Room type does not exist"
            );
        }

        if (roomType.getStatus() != RoomTypeStatus.ENABLED.getCode()) {
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

        booking.setStatus(BookingStatus.PENDING.getCode());


        booking.setTotalPrice(BigDecimal.ZERO);

        int inserted =
                bookingMapper.insert(booking);

        if (inserted != 1
                || booking.getId() == null) {

            throw new BusinessException(
                    "Failed to create booking"
            );
        }

        /*
         * 创建 V1 原始价格版本。
         */
        bookingPricingService.createFullRepriceVersion(
                booking.getId(),
                booking.getRoomTypeId(),
                "ORIGINAL_BOOKING",
                "Initial booking price",
                currentUserId
        );

        /*
         * 为新 Booking 创建空 Folio创建 Folio 不代表已经收费。
         * 此时：
         * totalAmount = 0
         * paidAmount = 0
         * balanceAmount = 0
         * status = COMPLETED (zero balance; not finally closed)
         */
        folioService.ensureFolioExists(
                booking.getId()
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
        require(!booking.getCheckInDate().isBefore(LocalDate.now(clock)), "Expired arrivals cannot be assigned or repriced");

        if (booking.getStatus() != BookingStatus.PENDING.getCode()) {
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

        require(java.util.Set.of(RoomStatus.AVAILABLE.getCode(),RoomStatus.BOOKED.getCode(),RoomStatus.OCCUPIED.getCode()).contains(room.getStatus()), "Room is not available for reservation");
        require(roomConflicts.overlapping(room.getId(),bookingId,booking.getCheckInDate(),booking.getCheckOutDate()).isEmpty(),"Room has an overlapping reservation or effective stay");

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
                        BookingStatus.APPROVED.getCode()
                );

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to approve booking"
            );
        }

        int roomUpdated =
                room.getStatus() == RoomStatus.AVAILABLE.getCode() ? roomMapper.transitionStatus(room.getId(), RoomStatus.AVAILABLE.getCode(), RoomStatus.BOOKED.getCode()) : 1;

        if (roomUpdated != 1) {
            throw new BusinessException(
                    "Failed to update room status"
            );
        }

        one(sysAuditLogMapper.insert(
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
        ));

        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO rejectBooking(Long bookingId, Long currentUserId) {
        Booking booking = lockBooking(bookingId);
        require(booking.getStatus() == BookingStatus.PENDING.getCode(), "Only pending bookings can be rejected");
        one(bookingMapper.transitionStatus(bookingId, BookingStatus.PENDING.getCode(), BookingStatus.REJECTED_BY_STAFF.getCode()));
        audit(booking, currentUserId, "REJECT_BOOKING");
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
        if (booking.getStatus() != BookingStatus.APPROVED.getCode()) {
            throw new BusinessException(
                    "Only approved bookings can be checked in"
            );
        }

        if (booking.getAssignedRoomId() == null) {
            throw new BusinessException(
                    "Booking does not have an assigned room"
            );
        }

        require(LocalDate.now(clock).equals(booking.getCheckInDate()), "Check-in is supported only on the contracted arrival date");
        validateSnapshot(booking);
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

        if (room.getStatus() != RoomStatus.BOOKED.getCode() && room.getStatus() != RoomStatus.AVAILABLE.getCode()) {
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
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);


        int bookingUpdated =
                bookingMapper.transitionStatus(bookingId, BookingStatus.APPROVED.getCode(), BookingStatus.CHECKED_IN.getCode());

        if (bookingUpdated != 1) {
            throw new BusinessException(
                    "Failed to update booking check-in status"
            );
        }

        int roomUpdated =
                roomMapper.transitionStatus(roomId, room.getStatus(), RoomStatus.OCCUPIED.getCode());

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

        one(sysAuditLogMapper.insert(
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
        ));
        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional(noRollbackFor=com.johnny.hotel.exception.WalletSettlementIncompleteException.class)
    public BookingVO checkOut(Long bookingId, Long currentUserId) {
        Booking booking = lockBooking(bookingId);
        require(booking.getStatus() == BookingStatus.CHECKED_IN.getCode(), "Only checked-in bookings can be checked out");

        require(booking.getAssignedRoomId() != null, "Booking has no current room");
        Room room = roomMapper.selectByIdForUpdate(booking.getAssignedRoomId());
        BookingRoomAssignment assignment = bookingRoomAssignmentMapper.selectActiveByBookingId(bookingId);
        require(assignment != null && assignment.getRoomId().equals(booking.getAssignedRoomId()), "Current assignment does not match booking");
        require(room != null && room.getStatus() == RoomStatus.OCCUPIED.getCode() && room.getRoomTypeId().equals(assignment.getRoomTypeId()), "Current room does not match active stay");
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (!checkoutFinalizer.finalizeStay(booking, now, currentUserId)) {throw new com.johnny.hotel.exception.WalletSettlementIncompleteException();}
        one(bookingRoomAssignmentMapper.closeAssignment(assignment.getId(), now));
        stayHistoryService.createForCompletedStay(bookingId);
        one(bookingMapper.transitionStatus(bookingId, BookingStatus.CHECKED_IN.getCode(), BookingStatus.CHECKED_OUT.getCode()));
        one(roomMapper.transitionStatus(room.getId(), RoomStatus.OCCUPIED.getCode(), RoomStatus.MAINTENANCE.getCode()));
        turnoverTasks.createForClosedAssignment(assignment.getId());
        audit(booking, currentUserId, "CHECK_OUT");
        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO cancelBooking(Long bookingId, Long currentUserId) {
        Booking booking = lockBooking(bookingId);
        require(booking.getUserId().equals(currentUserId), "You can only cancel your own booking");
        return cancelLocked(booking, currentUserId);
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
        dates(request.getCheckInDate(), request.getCheckOutDate());
        require(request.getGuestCount() != null && request.getGuestCount() > 0, "Guest count must be positive");

        Booking booking =
                bookingMapper.selectByIdForUpdate(bookingId);

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }
        validateSnapshot(booking);

        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException(
                    "You can only update your own booking"
            );
        }

        if (booking.getStatus() != BookingStatus.PENDING.getCode()) {
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
                .isBefore(LocalDate.now(clock))) {

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

        if (roomType.getStatus() != RoomTypeStatus.ENABLED.getCode()) {
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
        return cancelLocked(lockBooking(bookingId), currentUserId);
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
        require(!booking.getCheckInDate().isBefore(LocalDate.now(clock)), "Expired arrivals cannot be assigned or repriced");

        if (booking.getStatus() != BookingStatus.APPROVED.getCode()) {
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

        if (oldRoom.getStatus() == RoomStatus.DISABLED.getCode()) {
            throw new BusinessException(
                    "Currently assigned room is not in booked status"
            );
        }


        if (newRoom.getStatus() != RoomStatus.AVAILABLE.getCode() && newRoom.getStatus() != RoomStatus.BOOKED.getCode()) {
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

        int oldRoomUpdated = oldRoom.getStatus()==RoomStatus.BOOKED.getCode()
                ? roomMapper.transitionStatus(oldRoomId, RoomStatus.BOOKED.getCode(), RoomStatus.AVAILABLE.getCode()) : 1;

        if (oldRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to release previously assigned room"
            );
        }

        require(roomConflicts.overlapping(newRoomId,bookingId,booking.getCheckInDate(),booking.getCheckOutDate()).isEmpty(),"Target room has an overlapping reservation");
        int newRoomUpdated = newRoom.getStatus()==RoomStatus.BOOKED.getCode()?1:
                roomMapper.transitionStatus(newRoomId, RoomStatus.AVAILABLE.getCode(), RoomStatus.BOOKED.getCode());

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

        one(sysAuditLogMapper.insert(
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
        ));

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
        require(!booking.getCheckInDate().isBefore(LocalDate.now(clock)), "Expired arrivals cannot be assigned or repriced");
        validateSnapshot(booking);

        if (booking.getStatus() != BookingStatus.APPROVED.getCode()) {
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

        if (newRoomType.getStatus() != RoomTypeStatus.ENABLED.getCode()) {
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

        if (oldRoom.getStatus() == RoomStatus.DISABLED.getCode()) {
            throw new BusinessException(
                    "Currently assigned room is not in booked status"
            );
        }

        if (newRoom.getStatus() != RoomStatus.AVAILABLE.getCode() && newRoom.getStatus() != RoomStatus.BOOKED.getCode()) {
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


        int oldRoomUpdated = oldRoom.getStatus()==RoomStatus.BOOKED.getCode()
                ? roomMapper.transitionStatus(oldRoomId, RoomStatus.BOOKED.getCode(), RoomStatus.AVAILABLE.getCode()) : 1;

        if (oldRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to release previously assigned room"
            );
        }

        require(roomConflicts.overlapping(newRoomId,bookingId,booking.getCheckInDate(),booking.getCheckOutDate()).isEmpty(),"Target room has an overlapping reservation");
        int newRoomUpdated = newRoom.getStatus()==RoomStatus.BOOKED.getCode()?1:
                roomMapper.transitionStatus(newRoomId, RoomStatus.AVAILABLE.getCode(), RoomStatus.BOOKED.getCode());

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

        one(sysAuditLogMapper.insert(
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
        ));

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

        if (booking.getStatus() != BookingStatus.CHECKED_IN.getCode()) {
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

        stayPlan.mayChangeRoom(booking);
        var effectiveEnd=stayPlan.end(booking);
        require(!LocalDate.now(clock).isBefore(booking.getCheckInDate()) && !LocalDate.now(clock).isAfter(effectiveEnd), "Room change must be within effective stay dates");
        validateSnapshot(booking);
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

        if (oldRoom.getStatus() != RoomStatus.OCCUPIED.getCode()) {
            throw new BusinessException(
                    "Current room is not occupied"
            );
        }

        if (newRoom.getStatus() != RoomStatus.AVAILABLE.getCode() && newRoom.getStatus() != RoomStatus.BOOKED.getCode()) {
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

        if (newRoomType.getStatus() != RoomTypeStatus.ENABLED.getCode()) {
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
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

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
                roomMapper.transitionStatus(oldRoomId, RoomStatus.OCCUPIED.getCode(), RoomStatus.MAINTENANCE.getCode());

        if (oldRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to release current room for maintenance"
            );
        }

        turnoverTasks.createForClosedAssignment(currentAssignment.getId());
        require(roomConflicts.overlapping(newRoomId,bookingId,changeTime.toLocalDate(),effectiveEnd.isAfter(changeTime.toLocalDate())?effectiveEnd:effectiveEnd.plusDays(1)).isEmpty(),"Target room has an overlapping future reservation");
        int newRoomUpdated = roomMapper.transitionStatus(newRoomId, newRoom.getStatus(), RoomStatus.OCCUPIED.getCode());

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
                        .checkOutDate(effectiveEnd)
                        .reason(normalizeReason(request.getReason()))
                        .operatorId(currentUserId)
                        .build()
        );

        one(sysAuditLogMapper.insert(
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
        ));

        return getBookingByIdInternal(
                bookingId
        );
    }


    private Booking lockBooking(Long id) {
        Booking b = bookingMapper.selectByIdForUpdate(id);
        require(b != null, "Booking does not exist");
        return b;
    }
    private void validateSnapshot(Booking b) {
        Folio f = folioMapper.selectByBookingId(b.getId());
        require(f != null, "Booking folio does not exist");
        snapshotValidator.validate(b, f.getCurrency());
    }
    private void audit(Booking b, Long operator, String action) {
        one(sysAuditLogMapper.insert(SysAuditLog.builder().operatorId(operator).targetUserId(b.getUserId())
                .action(action).detail("Booking id: " + b.getId()).build()));
    }
    private BookingVO cancelLocked(Booking b, Long operator) {
        require(b.getStatus() == BookingStatus.PENDING.getCode() || b.getStatus() == BookingStatus.APPROVED.getCode(), "Only pending or approved bookings can be cancelled");
        require(bookingRoomAssignmentMapper.selectActiveByBookingId(b.getId()) == null, "Booking has an active stay");
        if (b.getStatus() == BookingStatus.APPROVED.getCode()) {
            require(b.getAssignedRoomId() != null, "Approved booking has no room");
            Room room = roomMapper.selectByIdForUpdate(b.getAssignedRoomId());
            require(room != null, "Reserved room is missing");
            if(room.getStatus()==RoomStatus.BOOKED.getCode())one(roomMapper.transitionStatus(room.getId(), RoomStatus.BOOKED.getCode(), RoomStatus.AVAILABLE.getCode()));
        } else require(b.getAssignedRoomId() == null, "Pending booking unexpectedly reserves a room");
        one(bookingMapper.transitionStatus(b.getId(), b.getStatus(), BookingStatus.CANCELLED_BY_USER.getCode()));
        audit(b, operator, "CANCEL_BOOKING");
        return getBookingByIdInternal(b.getId());
    }
}
