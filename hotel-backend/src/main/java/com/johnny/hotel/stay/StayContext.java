package com.johnny.hotel.stay;

import com.johnny.hotel.entity.Booking;
import com.johnny.hotel.mapper.BookingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Immutable Stay -> Reservation navigation. Writers retain Booking -> Stay lock ordering. */
@Component @RequiredArgsConstructor
public class StayContext {
    private final StayMapper stays;
    private final BookingMapper bookings;
    public Booking reservation(Long stayId) {
        var stay=stays.find(stayId);if(stay==null)throw new com.johnny.hotel.exception.BusinessException(404,"Stay not found");
        var booking=bookings.selectById(stay.getBookingId());require(booking!=null,"Reservation does not exist");return booking;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Booking lockReservation(Long stayId) {
        var identity=stays.find(stayId);if(identity==null)throw new com.johnny.hotel.exception.BusinessException(404,"Stay not found");
        var booking=bookings.selectByIdForUpdate(identity.getBookingId());
        require(booking!=null && stays.lock(stayId)!=null,"Stay reservation is missing");return booking;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean inHouse(Long stayId) {
        var stay=stays.lock(stayId);return stay!=null && stay.getStatus()==StayStatus.IN_HOUSE.getCode();
    }
}
