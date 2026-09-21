package com.johnny.hotel.walkin;

import com.johnny.hotel.guest.*;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.BookingPriceVersionMapper;
import com.johnny.hotel.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.List;
import java.util.Objects;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class WalkInBookingService {
    private final BookingMapper bookings;
    private final BookingService bookingService;
    private final GuestService guests;
    private final GuestAccess access;
    private final BookingPriceVersionMapper prices;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalkInViews.Created create(WalkInRequests.Create request,Long operator) {
        access.employee(operator);require(request!=null,"Walk-in request is required");
        require(bookings.ensureWalkInRequest(request.getRequestKey())>=1,"Walk-in request lock failed");
        bookings.lockWalkInRequest(request.getRequestKey());
        // A locking read is required under MySQL REPEATABLE READ. A plain SELECT
        // can retain a snapshot taken before a concurrent creator commits.
        var existing=bookings.selectByWalkInRequestKeyForUpdate(request.getRequestKey());
        if(existing!=null){
            require(existing.getRoomTypeId().equals(request.getRoomTypeId())&&Objects.equals(existing.getReservedRoomId(),request.getReservedRoomId())
                    && existing.getGuestCount().equals(request.getGuestCount())&&existing.getCheckInDate().equals(request.getCheckInDate())
                    && existing.getCheckOutDate().equals(request.getCheckOutDate()),"Walk-in request key already represents another reservation");
            return view(existing.getId(),operator);
        }
        Long booker=resolve(request.getBookerGuestId(),request.getBookerProfile(),operator,"booker");
        Long primary=request.getPrimaryGuestId()==null&&request.getPrimaryProfile()==null?booker:
                resolve(request.getPrimaryGuestId(),request.getPrimaryProfile(),operator,"primary guest");
        var booking=bookingService.createWalkInContract(WalkInContractCommand.builder().bookerGuestId(booker)
                .roomTypeId(request.getRoomTypeId()).reservedRoomId(request.getReservedRoomId()).guestCount(request.getGuestCount())
                .checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate()).requestKey(request.getRequestKey()).build(),operator);
        guests.addToBooking(booking.getId(),GuestRequests.Add.builder().guestId(primary).role(GuestRole.PRIMARY).build(),operator);
        for(var accompanying:request.getAccompanyingGuests()==null?List.<GuestRequests.Add>of():request.getAccompanyingGuests()){
            require(accompanying!=null&&accompanying.getRole()==GuestRole.ACCOMPANYING,"Walk-in accompanying guest role is required");
            guests.addToBooking(booking.getId(),accompanying,operator);
        }
        guests.confirm(booking.getId(),operator);
        return view(booking.getId(),operator);
    }
    private Long resolve(Long id,GuestRequests.Profile profile,Long actor,String label){
        require((id==null)!=(profile==null),"Provide exactly one "+label+" GuestProfile reference or profile");
        return id!=null?guests.get(id,actor).id():guests.create(profile,actor).id();
    }
    private WalkInViews.Created view(Long bookingId,Long actor){
        var booking=bookings.selectById(bookingId);var rows=guests.bookingGuests(bookingId,actor);
        var primary=rows.stream().filter(x->x.role()==GuestRole.PRIMARY).findFirst().orElseThrow();
        var price=prices.selectActiveByBookingId(bookingId);
        return WalkInViews.Created.builder().bookingId(bookingId).booker(guests.get(booking.getBookerGuestProfileId(),actor))
                .primaryGuest(primary.guest()).reservationStatus(booking.getStatus()).reservationSource(booking.getReservationSource())
                .checkInDate(booking.getCheckInDate()).checkOutDate(booking.getCheckOutDate()).roomTypeId(booking.getRoomTypeId())
                .reservedRoomId(booking.getReservedRoomId()).totalPrice(booking.getTotalPrice()).currency(price.getCurrency())
                .registrationReady(true).build();
    }
}
