package com.johnny.hotel.stay;
import com.johnny.hotel.entity.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
import static com.johnny.hotel.service.support.BillingRules.*;
@Component @RequiredArgsConstructor
public class StayPlan {
    private final StayAdjustmentMapper adjustments;
    private final StayMapper stays;
    private List<StayAdjustment> rows(Booking b){var stay=stays.byBooking(b.getId());return stay==null?List.of():adjustments.forStay(stay.getId());}
    public LocalDate end(Booking b){return end(b,rows(b));}
    public static LocalDate end(Booking b,List<StayAdjustment> rows){
        LocalDate end=b.getCheckOutDate();
        for(var r:rows){require(r.getOldEnd().equals(end),"Stay adjustment end chain is inconsistent");end=r.getNewEnd();}
        return end;
    }
    public LocalDate chargedThrough(Booking b){return rows(b).stream().filter(r->r.getAdjustmentType()==1).map(StayAdjustment::getNewEnd).reduce(b.getCheckOutDate(),(a,c)->a.isAfter(c)?a:c);}
    public void mayChangeRoom(Booking b){require(rows(b).stream().noneMatch(r->r.getAdjustmentType()==2),"Early departure has already been processed");}
}
