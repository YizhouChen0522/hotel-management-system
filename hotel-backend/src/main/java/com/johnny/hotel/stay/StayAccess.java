package com.johnny.hotel.stay;

import com.johnny.hotel.entity.Booking;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.guest.GuestAccess;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.wallet.RefundAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class StayAccess {
    private final RefundAccess identity;
    private final GuestAccess roles;
    private final BookingMapper bookings;
    private final StayMapper stays;
    public Long actor(){return identity.actor();}
    public Long customerScope() {
        Long actor=actor();if(identity.isCustomer(actor))return actor;
        identity.operational(actor,false);return null;
    }
    public void operate(Long actor){if(!actor().equals(actor))throw new org.springframework.security.access.AccessDeniedException("Stay access denied");roles.employee(actor);}
    public Stay read(Long id) {
        Long owner=customerScope();var stay=stays.find(id);
        Booking booking=stay==null?null:bookings.selectById(stay.getBookingId());
        if(booking==null || owner!=null && !owner.equals(booking.getUserId()))throw new BusinessException(404,"Stay not found");
        return stay;
    }
}
