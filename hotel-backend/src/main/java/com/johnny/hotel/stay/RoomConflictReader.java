package com.johnny.hotel.stay;
import com.johnny.hotel.entity.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.List;
/** Caller holds the Room row. Read committed on a separate connection avoids locking another Booking
 * in reverse order and avoids an outer repeatable-read snapshot. Every reservation writer owns Room first. */
@Service @RequiredArgsConstructor
public class RoomConflictReader {
    private final RoomConflictMapper mapper;
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,readOnly=true)
    public List<Booking> overlapping(Long room,Long excluded,LocalDate start,LocalDate end){return mapper.overlapping(room,excluded,start,end);}
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,readOnly=true)
    public List<Booking> arriving(Long room,Long excluded,LocalDate date){return mapper.arriving(room,excluded,date);}
}
