package com.johnny.hotel.service.impl;

import com.johnny.hotel.config.CacheConfig;
import com.johnny.hotel.dto.RoomTypeRequest;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.enums.RoomTypeStatus;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.service.RoomTypeService;
import com.johnny.hotel.vo.RoomTypeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomTypeServiceImpl implements RoomTypeService {

    private final RoomTypeMapper roomTypeMapper;

    private RoomTypeVO toVO(RoomType roomType) {
        return RoomTypeVO.builder()
                .id(roomType.getId())
                .typeName(roomType.getTypeName())
                .description(roomType.getDescription())
                .basePrice(roomType.getBasePrice())
                .capacity(roomType.getCapacity())
                .status(roomType.getStatus())
                .createTime(roomType.getCreateTime())
                .updateTime(roomType.getUpdateTime())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(
            cacheNames = CacheConfig.ROOM_TYPE_LIST,
            key = "'all'"
    )
    public RoomTypeVO createRoomType(RoomTypeRequest request) {

        RoomType roomType = RoomType.builder()
                .typeName(request.getTypeName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .capacity(request.getCapacity())
                .status(RoomTypeStatus.ENABLED.getCode())
                .build();

        com.johnny.hotel.service.support.BillingRules.one(roomTypeMapper.insert(roomType));

        RoomType created = roomTypeMapper.selectById(roomType.getId());

        return toVO(created);
    }

    @Override
    @Cacheable(
            cacheNames = CacheConfig.ROOM_TYPE_DETAIL,
            key = "#id",
            unless = "#result == null",
            condition = "!T(org.springframework.transaction.support.TransactionSynchronizationManager).isActualTransactionActive()"
    )
    public RoomTypeVO getRoomTypeById(Long id) {

        RoomType roomType = roomTypeMapper.selectById(id);

        if (roomType == null) {
            throw new BusinessException("Room type does not exist");
        }

        return toVO(roomType);
    }

    @Override
    public RoomTypeVO getRoomTypeByName(String typeName) {

        RoomType roomType = roomTypeMapper.selectByTypeName(typeName);

        if (roomType == null) {
            throw new BusinessException("Room type does not exist");
        }

        return toVO(roomType);
    }

    @Override
    @Cacheable(
            cacheNames = CacheConfig.ROOM_TYPE_LIST,
            key = "'all'",
            condition = "!T(org.springframework.transaction.support.TransactionSynchronizationManager).isActualTransactionActive()"
    )
    public List<RoomTypeVO> getRoomTypes() {

        return roomTypeMapper.selectAll()
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = CacheConfig.ROOM_TYPE_DETAIL,
                    key = "#id"
            ),
            @CacheEvict(
                    cacheNames = CacheConfig.ROOM_TYPE_LIST,
                    key = "'all'"
            )
    })
    public RoomTypeVO updateRoomType(Long id, RoomTypeRequest request) {

        RoomType existing = roomTypeMapper.selectById(id);

        if (existing == null) {
            throw new BusinessException("Room type does not exist with ID: " + id);
        }

        RoomType roomType = RoomType.builder()
                .id(id)
                .typeName(request.getTypeName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .capacity(request.getCapacity())
                .build();

        com.johnny.hotel.service.support.BillingRules.one(roomTypeMapper.update(roomType));

        RoomType updated = roomTypeMapper.selectById(id);

        return toVO(updated);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = CacheConfig.ROOM_TYPE_DETAIL,
                    key = "#id"
            ),
            @CacheEvict(
                    cacheNames = CacheConfig.ROOM_TYPE_LIST,
                    key = "'all'"
            )
    })
    public void enableRoomType(Long id) {

        RoomType existing = roomTypeMapper.selectById(id);

        if (existing == null) {
            throw new BusinessException("Room type does not exist with ID: " + id);
        }

        com.johnny.hotel.service.support.BillingRules.one(roomTypeMapper.updateStatus(id, RoomTypeStatus.ENABLED.getCode()));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = CacheConfig.ROOM_TYPE_DETAIL,
                    key = "#id"
            ),
            @CacheEvict(
                    cacheNames = CacheConfig.ROOM_TYPE_LIST,
                    key = "'all'"
            )
    })
    public void disableRoomType(Long id) {

        RoomType existing = roomTypeMapper.selectById(id);

        if (existing == null) {
            throw new BusinessException("Room type does not exist with ID: " + id);
        }

        com.johnny.hotel.service.support.BillingRules.one(roomTypeMapper.updateStatus(id, RoomTypeStatus.DISABLED.getCode()));
    }
}