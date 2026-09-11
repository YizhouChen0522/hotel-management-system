package com.johnny.hotel.stay;
import com.johnny.hotel.dto.pricing.RoomPriceQuote;
import com.johnny.hotel.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
@Service @RequiredArgsConstructor
public class StayPricing {
    private final PricingService pricing;
    /** A fresh MySQL snapshot, never a display cache or the caller's old RR snapshot. */
    @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RoomPriceQuote quote(Long type,LocalDate start,LocalDate end){return pricing.quoteRoomType(type,start,end);}
}
