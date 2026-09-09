package com.johnny.hotel.vo;
import lombok.Builder;
import com.johnny.hotel.entity.RoomRate;
import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record RoomRateVO(Long id,Long roomTypeId,LocalDate rateDate,BigDecimal price,String rateSource,String description) {
    public static RoomRateVO from(RoomRate r){return RoomRateVO.builder().id(r.getId()).roomTypeId(r.getRoomTypeId()).rateDate(r.getRateDate()).price(r.getPrice()).rateSource(r.getRateSource()).description(r.getDescription()).build();}
}
