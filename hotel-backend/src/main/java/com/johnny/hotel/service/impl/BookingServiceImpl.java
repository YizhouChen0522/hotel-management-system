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
    private final com.johnny.hotel.booking.deposit.DepositService deposits;
    private final com.johnny.hotel.stay.StayMapper stays;
    private final com.johnny.hotel.stay.StayApplicationService stayApplication;
    private final com.johnny.hotel.stay.RoomConflictReader roomConflicts;
    private final com.johnny.hotel.service.support.PriceSnapshotValidator snapshotValidator;
    private final BookingMapper bookingMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final RoomMapper roomMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final BookingPriceVersionMapper bookingPriceVersionMapper;
    private final BookingPricingService bookingPricingService;
    private final com.johnny.hotel.guest.GuestMapper guestMapper;
    private final SysUserMapper sysUserMapper;
    private final com.johnny.hotel.guest.GuestAccess guestAccess;
    private final com.johnny.hotel.reservation.ReservationPolicyService reservationPolicies;
    private final com.johnny.hotel.reservation.ReservationLifecycleService reservationLifecycle;

    private BookingVO toVO(com.johnny.hotel.entity.Booking booking) {
        RoomType roomType = roomTypeMapper.selectById(booking.getRoomTypeId());

        Room assignedRoom = null;
        if (booking.getReservedRoomId() != null) {
            assignedRoom = roomMapper.selectById(booking.getReservedRoomId());
        }

        return BookingVO.builder()
                .reservationSource(booking.getReservationSource())
                .reservationPolicyId(booking.getReservationPolicyId())
                .cancellationPolicy(booking.getReservationPolicyId()==null?null:reservationPolicies.bound(booking.getReservationPolicyId()))
                .id(booking.getId())
                .userId(booking.getUserId())
                .bookerGuestProfileId(booking.getBookerGuestProfileId())
                .createdByUserId(booking.getCreatedByUserId())
                .roomTypeId(booking.getRoomTypeId())
                .roomTypeName(roomType == null ? null : roomType.getTypeName())
                .reservedRoomId(booking.getReservedRoomId())
                .reservedRoomNumber(assignedRoom == null ? null : assignedRoom.getRoomNumber())
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

        require(request.getRequestKey()!=null&&request.getRequestKey().matches("[A-Za-z0-9_-]{8,64}"),"Invalid portal reservation request key");
        require(bookingMapper.ensurePortalRequest(request.getRequestKey())>=1,"Portal request lock failed");
        require(request.getRequestKey().equals(bookingMapper.lockPortalRequest(request.getRequestKey())),"Portal request lock failed");
        var existingPortal=bookingMapper.selectByPortalRequestKeyForUpdate(request.getRequestKey());
        if(existingPortal!=null){
            require(currentUserId.equals(existingPortal.getUserId())
                    && request.getRoomTypeId().equals(existingPortal.getRoomTypeId())
                    && request.getGuestCount().equals(existingPortal.getGuestCount())
                    && request.getCheckInDate().equals(existingPortal.getCheckInDate())
                    && request.getCheckOutDate().equals(existingPortal.getCheckOutDate()),
                    "Portal request key already represents another reservation");
            validateSnapshot(existingPortal);
            deposits.validatePortalFullDeposit(existingPortal.getId());
            return toVO(existingPortal);
        }
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

        var booker=guestMapper.byUser(currentUserId);
        if(booker==null){
            var user=sysUserMapper.selectById(currentUserId);require(user!=null,"Customer account does not exist");
            String display=user.getRealName()==null||user.getRealName().isBlank()?user.getUsername():user.getRealName().trim();
            String[] names=display.split("\\s+",2);
            booker=com.johnny.hotel.guest.GuestProfile.builder().linkedUserId(currentUserId).firstName(names[0])
                    .lastName(names.length==2?names[1]:names[0]).phone(user.getPhone()).email(user.getEmail()).status(1).build();
            one(guestMapper.insertProfile(booker));
        }
        booking.setUserId(currentUserId);
        booking.setBookerGuestProfileId(booker.getId());
        booking.setCreatedByUserId(currentUserId);
        booking.setReservationSource(com.johnny.hotel.enums.ReservationSource.CUSTOMER_PORTAL.name());
        booking.setPortalRequestKey(request.getRequestKey());
        var activePolicy=reservationPolicies.activeForBooking(false);
        booking.setReservationPolicyId(activePolicy==null?null:activePolicy.getId());
        booking.setRoomTypeId(request.getRoomTypeId());
        booking.setReservedRoomId(null);
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

        deposits.openForReservation(booking.getId());
        var accepted=bookingMapper.selectByIdForUpdate(booking.getId());
        var receipt=deposits.receivePortalWallet(booking.getId(),accepted.getTotalPrice(),request.getRequestKey(),currentUserId);
        require(receipt.getAmount().compareTo(accepted.getTotalPrice())==0,"Full reservation deposit is required");
        deposits.validatePortalFullDeposit(booking.getId());

        return getBookingByIdInternal(
                booking.getId()
        );
    }

    @Override
    @Transactional
    public BookingVO createWalkInContract(com.johnny.hotel.walkin.WalkInContractCommand command,Long operatorId) {
        guestAccess.employee(operatorId);
        require(command!=null && command.requestKey()!=null && command.requestKey().matches("[A-Za-z0-9_-]{8,64}"),"Invalid walk-in request key");
        require(bookingMapper.ensureWalkInRequest(command.requestKey())>=1,"Walk-in request lock failed");
        require(command.requestKey().equals(bookingMapper.lockWalkInRequest(command.requestKey())),"Walk-in request lock failed");
        var existing=bookingMapper.selectByWalkInRequestKeyForUpdate(command.requestKey());
        if(existing!=null){
            require(existing.getBookerGuestProfileId().equals(command.bookerGuestId())
                    && existing.getRoomTypeId().equals(command.roomTypeId())
                    && java.util.Objects.equals(existing.getReservedRoomId(),command.reservedRoomId())
                    && existing.getGuestCount().equals(command.guestCount())
                    && existing.getCheckInDate().equals(command.checkInDate())
                    && existing.getCheckOutDate().equals(command.checkOutDate()),"Walk-in request key already represents another reservation");
            return toVO(existing);
        }
        dates(command.checkInDate(),command.checkOutDate());
        require(command.checkInDate().equals(LocalDate.now(clock)),"Walk-in arrival must be the current hotel date");
        require(command.guestCount()!=null&&command.guestCount()>0,"Guest count must be positive");
        var booker=guestMapper.profile(command.bookerGuestId());require(booker!=null&&Integer.valueOf(1).equals(booker.getStatus()),"Active booker GuestProfile is required");
        var type=roomTypeMapper.selectById(command.roomTypeId());require(type!=null&&type.getStatus()==RoomTypeStatus.ENABLED.getCode(),"Room type does not exist or is disabled");
        require(command.guestCount()<=type.getCapacity(),"Guest count exceeds room type capacity");
        var walkInPolicy=reservationPolicies.activeForBooking(false);
        var booking=Booking.builder().userId(null).bookerGuestProfileId(command.bookerGuestId()).createdByUserId(operatorId)
                .roomTypeId(command.roomTypeId()).reservedRoomId(null).reservationSource(com.johnny.hotel.enums.ReservationSource.WALK_IN.name()).walkInRequestKey(command.requestKey())
                .reservationPolicyId(walkInPolicy==null?null:walkInPolicy.getId())
                .guestCount(command.guestCount()).checkInDate(command.checkInDate()).checkOutDate(command.checkOutDate())
                .status(BookingStatus.PENDING.getCode()).totalPrice(BigDecimal.ZERO).build();
        one(bookingMapper.insert(booking));require(booking.getId()!=null,"Failed to create walk-in reservation");
        bookingPricingService.createFullRepriceVersion(booking.getId(),booking.getRoomTypeId(),"ORIGINAL_BOOKING","Walk-in contract",operatorId);
        var approval=new ApproveBookingRequest();approval.setAssignedRoomId(command.reservedRoomId());
        approveBooking(booking.getId(),approval,operatorId);
        one(sysAuditLogMapper.insert(SysAuditLog.builder().operatorId(operatorId).targetUserId(null).action("CREATE_WALK_IN_BOOKING")
                .detail("Booking "+booking.getId()+", booker guest "+command.bookerGuestId()+", request "+command.requestKey()).build()));
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
        require(stays.byBooking(bookingId)==null,"Reservation already converted to a Stay");
        require(!booking.getCheckInDate().isBefore(LocalDate.now(clock)), "Expired arrivals cannot be assigned or repriced");

        if (booking.getStatus() != BookingStatus.PENDING.getCode()) {
            throw new BusinessException(
                    "Only pending bookings can be approved"
            );
        }

        deposits.requireFullGuarantee(bookingId);

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
        stayApplication.checkIn(bookingId,currentUserId);
        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional(noRollbackFor=com.johnny.hotel.exception.WalletSettlementIncompleteException.class)
    public BookingVO checkOut(Long bookingId, Long currentUserId) {
        stayApplication.checkOut(stayApplication.stayIdForBooking(bookingId),currentUserId);
        return getBookingByIdInternal(bookingId);
    }

    @Override
    @Transactional
    public BookingVO cancelBooking(Long bookingId, Long currentUserId) {
        reservationLifecycle.cancel(bookingId,currentUserId,"CUSTOMER","Customer requested cancellation");
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

        if (!currentUserId.equals(booking.getUserId())) {
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

        if (!currentUserId.equals(booking.getUserId())) {
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

        require(!roomTypeChanged && !dateChanged,
                "Paid reservation dates and room type cannot be repriced; cancel and create a new reservation");

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

        return getBookingByIdInternal(
                bookingId
        );
    }

    @Override
    @Transactional
    public BookingVO cancelBookingByAdmin(Long bookingId, Long currentUserId) {
        reservationLifecycle.cancel(bookingId,currentUserId,"HOTEL","Hotel initiated cancellation");
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
        require(stays.byBooking(bookingId)==null,"Reservation already converted to a Stay");
        require(!booking.getCheckInDate().isBefore(LocalDate.now(clock)), "Expired arrivals cannot be assigned or repriced");

        if (booking.getStatus() != BookingStatus.APPROVED.getCode()) {
            throw new BusinessException(
                    "Only approved bookings can have their room reassigned"
            );
        }
        Long oldRoomId = booking.getReservedRoomId();
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
        require(stays.byBooking(bookingId)==null,"Reservation already converted to a Stay");
        require(!booking.getCheckInDate().isBefore(LocalDate.now(clock)), "Expired arrivals cannot be assigned or repriced");
        validateSnapshot(booking);

        if (booking.getStatus() != BookingStatus.APPROVED.getCode()) {
            throw new BusinessException(
                    "Only approved bookings can change room type"
            );
        }
        deposits.requireUnfundedForContractReprice(bookingId);

        if (booking.getReservedRoomId() == null) {
            throw new BusinessException(
                    "Booking does not currently have an assigned room"
            );
        }

        Long oldRoomId =
                booking.getReservedRoomId();

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
        stayApplication.changeRoomDuringStay(stayApplication.stayIdForBooking(bookingId),request,currentUserId);
        return getBookingByIdInternal(bookingId);
    }


    private Booking lockBooking(Long id) {
        Booking b = bookingMapper.selectByIdForUpdate(id);
        require(b != null, "Booking does not exist");
        return b;
    }
    private void validateSnapshot(Booking b) {
        var price=bookingPriceVersionMapper.selectActiveByBookingId(b.getId());
        require(price!=null,"Reservation price snapshot is missing");
        snapshotValidator.validate(b,price.getCurrency());
    }
    private void audit(Booking b, Long operator, String action) {
        one(sysAuditLogMapper.insert(SysAuditLog.builder().operatorId(operator).targetUserId(b.getUserId())
                .action(action).detail("Booking id: " + b.getId()).build()));
    }
}
