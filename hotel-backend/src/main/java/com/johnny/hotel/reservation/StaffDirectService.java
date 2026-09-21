package com.johnny.hotel.reservation;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.enums.*;
import com.johnny.hotel.guest.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class StaffDirectService {
    private final ReservationAccess access;
    private final ReservationPolicyService policies;
    private final BookingMapper bookings;
    private final RoomTypeMapper roomTypes;
    private final GuestService guests;
    private final BookingPricingService prices;
    private final DepositService deposits;
    private final DepositMapper depositMapper;
    private final BookingService bookingService;
    private final SysAuditLogMapper audits;
    private final Clock clock;

    public record Created(Long bookingId,Long bookerGuestId,Long primaryGuestId,Long reservedRoomId,
                          Long policyId,BigDecimal acceptedQuote,Long depositPaymentId,Integer status) {}

    private Long guest(Long id,GuestRequests.Profile profile,Long operator,String label){
        require((id==null)!=(profile==null),"Provide exactly one "+label+" GuestProfile reference or profile");
        return id!=null?guests.get(id,operator).id():guests.create(profile,operator).id();
    }
    private String normalized(String value){return value==null||value.isBlank()?null:value.trim();}
    private boolean sameProfile(GuestRequests.Profile request,GuestViews.Profile stored){
        if(request==null)return true;
        return Objects.equals(normalized(request.getFirstName()),stored.firstName())
                &&Objects.equals(normalized(request.getLastName()),stored.lastName())
                &&Objects.equals(normalized(request.getPhone()),stored.phone())
                &&Objects.equals(normalized(request.getEmail()),stored.email())
                &&Objects.equals(normalized(request.getGender()),stored.gender())
                &&Objects.equals(request.getDateOfBirth(),stored.dateOfBirth())
                &&Objects.equals(normalized(request.getNationality()),stored.nationality())
                &&Objects.equals(normalized(request.getDocumentType()),stored.documentType())
                &&Objects.equals(normalized(request.getDocumentNumber()),stored.documentNumber())
                &&Objects.equals(normalized(request.getIssuingCountry()),stored.issuingCountry())
                &&Objects.equals(request.getDocumentExpiryDate(),stored.documentExpiryDate())
                &&Objects.equals(normalized(request.getNotes()),stored.notes());
    }
    private Created view(Booking b,Long actor){
        var account=depositMapper.byBooking(b.getId());require(account!=null,"Deposit account is missing");
        var receipt=depositMapper.payments(account.getId()).stream().filter(p->p.getRequestKey().equals(b.getStaffDirectRequestKey())).findFirst().orElseThrow();
        var primary=guests.bookingGuests(b.getId(),actor).stream().filter(x->x.role()==GuestRole.PRIMARY).findFirst().orElseThrow();
        return new Created(b.getId(),b.getBookerGuestProfileId(),primary.guest().id(),b.getReservedRoomId(),
                b.getReservationPolicyId(),b.getTotalPrice(),receipt.getId(),b.getStatus());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Created create(StaffDirectRequests.Create request){
        var actor=access.hotel();
        require(request!=null&&request.getRequestKey()!=null&&request.getRequestKey().matches("[A-Za-z0-9_-]{8,64}"),"Invalid Staff Direct request key");
        require(bookings.ensureStaffDirectRequest(request.getRequestKey())>=1,"Request lock failed");
        require(request.getRequestKey().equals(bookings.lockStaffDirectRequest(request.getRequestKey())),"Request lock failed");
        var existing=bookings.selectByStaffDirectRequestKeyForUpdate(request.getRequestKey());
        if(existing!=null){
            var account=depositMapper.byBooking(existing.getId());
            var receipt=account==null?null:depositMapper.payments(account.getId()).stream()
                    .filter(p->p.getRequestKey().equals(request.getRequestKey())).findFirst().orElse(null);
            var primary=guests.bookingGuests(existing.getId(),actor.id()).stream()
                    .filter(x->x.role()==GuestRole.PRIMARY).findFirst().orElse(null);
            require(Objects.equals(existing.getRoomTypeId(),request.getRoomTypeId())&&Objects.equals(existing.getReservedRoomId(),request.getReservedRoomId())
                    &&Objects.equals(existing.getGuestCount(),request.getGuestCount())&&Objects.equals(existing.getCheckInDate(),request.getCheckInDate())
                    &&Objects.equals(existing.getCheckOutDate(),request.getCheckOutDate())&&receipt!=null
                    &&Objects.equals(receipt.getPaymentMethod(),request.getPaymentMethod())&&Objects.equals(receipt.getReferenceNo(),request.getReceiptReference())
                    &&(request.getBookerGuestId()==null||request.getBookerGuestId().equals(existing.getBookerGuestProfileId()))
                    &&sameProfile(request.getBookerProfile(),guests.get(existing.getBookerGuestProfileId(),actor.id()))
                    &&primary!=null&&Objects.equals(request.getPrimaryGuestId()==null&&request.getPrimaryProfile()==null
                            ?existing.getBookerGuestProfileId():request.getPrimaryGuestId()==null?primary.guest().id():request.getPrimaryGuestId(),primary.guest().id())
                    &&sameProfile(request.getPrimaryProfile(),primary.guest()),
                    "Staff Direct request key already represents another reservation");
            return view(existing,actor.id());
        }
        require(request.getReservedRoomId()!=null,"A concrete room is required");
        require(request.getCheckInDate()!=null&&!request.getCheckInDate().isBefore(LocalDate.now(clock))
                &&request.getCheckOutDate()!=null&&request.getCheckOutDate().isAfter(request.getCheckInDate()),"Invalid future stay dates");
        require(request.getGuestCount()!=null&&request.getGuestCount()>0,"Guest count must be positive");
        var type=roomTypes.selectById(request.getRoomTypeId());
        require(type!=null&&type.getStatus()==RoomTypeStatus.ENABLED.getCode()&&request.getGuestCount()<=type.getCapacity(),"Invalid room type or guest count");
        var policy=policies.activeForBooking(true);
        Long booker=guest(request.getBookerGuestId(),request.getBookerProfile(),actor.id(),"booker");
        Long primary=request.getPrimaryGuestId()==null&&request.getPrimaryProfile()==null?booker:
                guest(request.getPrimaryGuestId(),request.getPrimaryProfile(),actor.id(),"primary guest");
        var b=Booking.builder().userId(null).bookerGuestProfileId(booker).createdByUserId(actor.id())
                .roomTypeId(request.getRoomTypeId()).reservedRoomId(null).reservationSource(ReservationSource.STAFF_DIRECT.name())
                .staffDirectRequestKey(request.getRequestKey()).reservationPolicyId(policy.getId()).guestCount(request.getGuestCount())
                .checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate())
                .status(BookingStatus.PENDING.getCode()).totalPrice(BigDecimal.ZERO).build();
        one(bookings.insert(b));
        prices.createFullRepriceVersion(b.getId(),b.getRoomTypeId(),"ORIGINAL_BOOKING","Staff Direct accepted quote",actor.id());
        var quoted=bookings.selectByIdForUpdate(b.getId());
        require(quoted.getTotalPrice()!=null&&quoted.getTotalPrice().signum()>0,"Accepted quote must be positive");
        deposits.openForReservation(b.getId());
        var receipt=deposits.receive(b.getId(),DepositRequests.Receive.builder().amount(quoted.getTotalPrice())
                .paymentMethod(request.getPaymentMethod()).referenceNo(request.getReceiptReference()).requestKey(request.getRequestKey()).build());
        require(receipt.getAmount().compareTo(quoted.getTotalPrice())==0,"Full deposit receipt is required");
        var assignment=new ApproveBookingRequest();assignment.setAssignedRoomId(request.getReservedRoomId());
        bookingService.approveBooking(b.getId(),assignment,actor.id());
        guests.addToBooking(b.getId(),GuestRequests.Add.builder().guestId(primary).role(GuestRole.PRIMARY).build(),actor.id());
        one(audits.insert(SysAuditLog.builder().operatorId(actor.id()).action("CREATE_STAFF_DIRECT_RESERVATION")
                .detail("Reservation "+b.getId()+", deposit receipt "+receipt.getId()).build()));
        return view(bookings.selectById(b.getId()),actor.id());
    }
}
