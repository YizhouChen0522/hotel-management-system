package com.johnny.hotel.stay;

import com.johnny.hotel.guest.*;
import com.johnny.hotel.mapper.StayRoomAssignmentMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class StayGuestService {
    private final StayMapper stays;
    private final StayContext context;
    private final StayAccess access;
    private final GuestService guests;
    private final GuestMapper profiles;
    private final StayRoomAssignmentMapper assignments;
    private final RoomTypeMapper roomTypes;
    @Builder public record Entry(Long id,Long stayId,Long guestId,GuestRole role,String firstName,String lastName) {}
    public List<Entry> list(Long stayId) {
        access.read(stayId);return stays.guests(stayId).stream().map(this::view).toList();
    }
    @Transactional public Entry addAccompanying(Long stayId,GuestRequests.Add request) {
        Long actor=access.actor();access.operate(actor);
        require(request!=null && request.getRole()==GuestRole.ACCOMPANYING,"Only accompanying guests can be added during a Stay");
        var booking=context.lockReservation(stayId);require(context.inHouse(stayId),"Historical Stay guests cannot be changed");
        var assignment=assignments.selectActiveByStayId(stayId);require(assignment!=null,"Actual room assignment is missing");
        var type=roomTypes.selectById(assignment.getRoomTypeId());
        var currentGuests=stays.guestsForUpdate(stayId);
        require(type!=null && currentGuests.size()<Math.min(booking.getGuestCount(),type.getCapacity()),"Actual guest count exceeds reservation or room capacity");
        Long guestId=request.getGuestId();
        if(guestId==null){require(request.getProfile()!=null,"Guest profile is required");guestId=guests.create(request.getProfile(),actor).id();}
        else guests.get(guestId,actor);
        Long selected=guestId;require(currentGuests.stream().noneMatch(g->g.getGuestId().equals(selected)),"Guest is already part of this Stay");
        var guest=StayGuest.builder().stayId(stayId).guestId(guestId).guestRole(GuestRole.ACCOMPANYING.getCode()).registeredBy(actor).build();
        one(stays.addGuest(guest));return view(guest);
    }
    private Entry view(StayGuest guest){var profile=profiles.profile(guest.getGuestId());return Entry.builder().id(guest.getId()).stayId(guest.getStayId()).guestId(guest.getGuestId())
            .role(GuestRole.fromCode(guest.getGuestRole())).firstName(profile.getFirstName()).lastName(profile.getLastName()).build();}
}
