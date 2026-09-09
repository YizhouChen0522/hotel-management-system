package com.johnny.hotel.service;
import com.johnny.hotel.config.CacheConfig;
import com.johnny.hotel.dto.RoomRateRequest;
import com.johnny.hotel.entity.RoomRate;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.vo.RoomRateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Cached configuration display only. PricingService deliberately continues using MySQL mappers. */
@Service @RequiredArgsConstructor
public class RoomRateService {
    private final RoomRateMapper rates;
    private final RoomTypeMapper types;
    @Cacheable(cacheNames=CacheConfig.ROOM_RATE_RANGE,key="#typeId + ':' + #start + ':' + #end",condition="!T(org.springframework.transaction.support.TransactionSynchronizationManager).isActualTransactionActive()")
    public List<RoomRateVO> range(Long typeId,LocalDate start,LocalDate end){
        require(typeId!=null&&typeId>0,"Room type id is required");dates(start,end);
        return rates.selectByRoomTypeIdAndDateRange(typeId,start,end).stream().map(RoomRateVO::from).toList();
    }
    @Transactional
    @CacheEvict(cacheNames=CacheConfig.ROOM_RATE_RANGE,allEntries=true)
    public RoomRateVO save(Long typeId,LocalDate date,RoomRateRequest request){
        require(date!=null&&request!=null,"Date and rate request are required");
        require(money(request.getPrice(),10).signum()>0,"Rate must be positive");
        require(request.getRateSource()!=null&&!request.getRateSource().isBlank()&&request.getRateSource().length()<=30,"Rate source is required and limited to 30 characters");
        require(request.getDescription()==null||request.getDescription().length()<=255,"Description is too long");
        require(types.selectByIdForUpdate(typeId)!=null,"Room type does not exist");
        RoomRate rate=rates.selectByRoomTypeIdAndDate(typeId,date);
        boolean create=rate==null;if(create){rate=new RoomRate();rate.setRoomTypeId(typeId);rate.setRateDate(date);}
        rate.setPrice(request.getPrice());rate.setRateSource(request.getRateSource().trim());rate.setDescription(request.getDescription());
        one(create?rates.insert(rate):rates.update(rate));return RoomRateVO.from(rate);
    }
    @Transactional
    @CacheEvict(cacheNames=CacheConfig.ROOM_RATE_RANGE,allEntries=true)
    public void remove(Long typeId,LocalDate date){
        require(date!=null,"Rate date is required");require(types.selectByIdForUpdate(typeId)!=null,"Room type does not exist");
        var rate=rates.selectByRoomTypeIdAndDate(typeId,date);require(rate!=null,"Rate override does not exist");one(rates.deleteById(rate.getId()));
    }
}
