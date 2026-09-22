package com.johnny.hotel.payment;

import com.johnny.hotel.pagination.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class HotelTransactionQueryService {
    private final PaymentCoreMapper db;private final PaginationSupport pages;
    public PageResult<HotelTransactionRecord> query(Integer page,Integer size,LocalDateTime from,LocalDateTime to,String direction,String sourceType,String method,String provider,String currency,String businessType,Long businessId,String providerRef){
        var w=pages.window(page,size,true);int limit=pages.limit(w);
        return pages.result(w,db.transactionPage(from,to,n(direction),n(sourceType),n(method),n(provider),n(currency),n(businessType),businessId,n(providerRef),w.offset(),limit),db.transactionCount(from,to,n(direction),n(sourceType),n(method),n(provider),n(currency),n(businessType),businessId,n(providerRef)));
    }
    private String n(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(java.util.Locale.ROOT);}
}
