package com.johnny.hotel.stay;
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
public class StayApplicationService {
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
    private final StayRoomAssignmentMapper stayRoomAssignmentMapper;
    private final BookingPriceVersionMapper bookingPriceVersionMapper;
    private final BookingNightlyRateMapper bookingNightlyRateMapper;
    private final FolioService folioService;
    private final BookingPricingService bookingPricingService;
    private final StayHistoryService stayHistoryService;
    private final com.johnny.hotel.service.RoomTurnoverTaskService turnoverTasks;
    private final com.johnny.hotel.guest.GuestService guestService;

    private final com.johnny.hotel.service.FolioFinancialService folioFinancialService;
    private final StayMapper stays;
    private final com.johnny.hotel.guest.GuestMapper guestMapper;
    private final com.johnny.hotel.guest.GuestAccess stayAccess;
    private final com.johnny.hotel.booking.deposit.DepositTransferService depositTransfers;
    @Transactional
    public Stay checkIn(
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
         * 4 CANCELLED
         * 5 REJECTED
         */
        if (booking.getStatus() != BookingStatus.APPROVED.getCode()) {
            throw new BusinessException(
                    "Only approved bookings can be checked in"
            );
        }

        stayAccess.employee(currentUserId);
        require(stays.byBooking(bookingId)==null,"Reservation already has a Stay");
        guestService.validateForCheckIn(bookingId, currentUserId);

        if (booking.getReservedRoomId() == null) {
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

        StayRoomAssignment existingAssignment =
                stayRoomAssignmentMapper
                        .selectActiveByBookingId(bookingId);

        if (existingAssignment != null) {
            throw new BusinessException(
                    "Booking already has an active room assignment"
            );
        }

        Long roomId =
                booking.getReservedRoomId();

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


        var registration=guestMapper.registration(bookingId);
        require(registration!=null && Boolean.TRUE.equals(registration.getRegistrationConfirmed()),"Guest registration is not confirmed");
        var stay=Stay.builder().bookingId(bookingId).primaryGuestId(registration.getPrimaryGuestId()).registrationId(registration.getId()).status(StayStatus.IN_HOUSE.getCode())
                .actualCheckInTime(checkInTime).checkedInBy(currentUserId).build();
        one(stays.insert(stay));
        for(var planned:guestMapper.bookingGuests(bookingId)) {
            one(stays.addGuest(StayGuest.builder().stayId(stay.getId()).guestId(planned.getGuestId()).guestRole(planned.getGuestRole()).registeredBy(currentUserId).build()));
        }
        folioService.ensureFolioExists(stay.getId());

        int roomUpdated =
                roomMapper.transitionStatus(roomId, room.getStatus(), RoomStatus.OCCUPIED.getCode());

        if (roomUpdated != 1) {
            throw new BusinessException(
                    "Failed to update room occupancy status"
            );
        }

        StayRoomAssignment assignment = StayRoomAssignment.builder()
                .stayId(stay.getId())
                .roomId(roomId)
                .roomTypeId(room.getRoomTypeId())
                .assignmentType("CHECK_IN")
                .startTime(checkInTime)
                .changeReason("Initial check-in")
                .createdBy(currentUserId)
                .build();

        int assignmentInserted =
                stayRoomAssignmentMapper
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
                stay.getId(),
                roomChargeCommands,
                currentUserId
        );

        depositTransfers.transferForCheckIn(stay.getId(),currentUserId);
        folioFinancialService.recalculateSummary(folioMapper.selectByStayIdForUpdate(stay.getId()).getId());
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
        return stays.byBooking(bookingId);
    }
    @Transactional(noRollbackFor=com.johnny.hotel.exception.WalletSettlementIncompleteException.class)
    public Stay checkOut(Long stayId, Long currentUserId) {
        stayAccess.employee(currentUserId);
        Stay stay=lockActualStay(stayId);Long bookingId=stay.getBookingId();
        Booking booking = bookingMapper.selectById(bookingId);

        var current=roomConflicts.activeAssignment(stayId);
        require(current!=null,"Stay has no active room assignment");
        Room room = roomMapper.selectByIdForUpdate(current.getRoomId());
        var assignment=stayRoomAssignmentMapper.selectActiveByStayId(stayId);
        require(assignment!=null && assignment.getId().equals(current.getId()),"Current assignment changed");
        require(room != null && room.getStatus() == RoomStatus.OCCUPIED.getCode() && room.getRoomTypeId().equals(assignment.getRoomTypeId()), "Current room does not match active stay");
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (!checkoutFinalizer.finalizeStay(stay,booking, now, currentUserId)) {throw new com.johnny.hotel.exception.WalletSettlementIncompleteException();}
        one(stayRoomAssignmentMapper.closeAssignment(assignment.getId(), now));
        stayHistoryService.createForCompletedStay(stayId);
        one(stays.close(stayId,now,currentUserId));
        one(roomMapper.transitionStatus(room.getId(), RoomStatus.OCCUPIED.getCode(), RoomStatus.MAINTENANCE.getCode()));
        turnoverTasks.createForClosedAssignment(assignment.getId(), currentUserId);
        audit(booking, currentUserId, "CHECK_OUT");
        return stays.byBooking(bookingId);
    }
    @Transactional
    public Stay changeRoomDuringStay(Long stayId, ChangeRoomDuringStayRequest request, Long currentUserId) {
        stayAccess.employee(currentUserId);
        Stay stay=lockActualStay(stayId);Long bookingId=stay.getBookingId();
        Booking booking=bookingMapper.selectById(bookingId);
        var active=roomConflicts.activeAssignment(stayId);
        require(active!=null,"Stay has no current assignment");
        Long oldRoomId=active.getRoomId();

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
        Map<Long, Room> lockedRooms = lockRoomsInOrder(oldRoomId, newRoomId);
        StayRoomAssignment currentAssignment=stayRoomAssignmentMapper.selectActiveByStayId(stayId);
        require(currentAssignment!=null && currentAssignment.getId().equals(active.getId()),"Current assignment changed");

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

        int assignmentClosed = stayRoomAssignmentMapper.closeAssignment(
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

        turnoverTasks.createForClosedAssignment(currentAssignment.getId(), currentUserId);
        require(roomConflicts.overlapping(newRoomId,bookingId,changeTime.toLocalDate(),effectiveEnd.isAfter(changeTime.toLocalDate())?effectiveEnd:effectiveEnd.plusDays(1)).isEmpty(),"Target room has an overlapping future reservation");
        int newRoomUpdated = roomMapper.transitionStatus(newRoomId, newRoom.getStatus(), RoomStatus.OCCUPIED.getCode());

        if (newRoomUpdated != 1) {
            throw new BusinessException(
                    "Failed to occupy new room"
            );
        }

        StayRoomAssignment newAssignment = StayRoomAssignment.builder()
                .stayId(stay.getId())
                .roomId(newRoomId)
                .roomTypeId(newRoom.getRoomTypeId())
                .assignmentType("ROOM_CHANGE")
                .startTime(changeTime)
                .endTime(null)
                .changeReason(normalizeReason(request.getReason()))
                .createdBy(currentUserId)
                .build();

        int assignmentInserted = stayRoomAssignmentMapper.insert(newAssignment);

        if (assignmentInserted != 1
                || newAssignment.getId() == null) {

            throw new BusinessException(
                    "Failed to create new room assignment"
            );
        }
        folioService.applyRoomChangeBilling(
                RoomChangeBillingCommand.builder()
                        .stayId(stayId)
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

        return stays.byBooking(bookingId);
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
    private Stay lockActualStay(Long stayId) {
        var identity=stays.find(stayId);require(identity!=null,"Stay does not exist");
        lockBooking(identity.getBookingId());
        var current=stays.lock(stayId);
        require(current!=null && current.getStatus()==StayStatus.IN_HOUSE.getCode(),"Stay is not in house");
        return current;
    }
    public Long stayIdForBooking(Long bookingId) {
        var stay=stays.byBooking(bookingId);require(stay!=null,"Reservation has no actual Stay");return stay.getId();
    }
}
