package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.RoomTypeRequest;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.service.RoomTypeService;
import com.johnny.hotel.vo.RoomTypeVO;
import lombok.RequiredArgsConstructor;
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
    public RoomTypeVO createRoomType(RoomTypeRequest request) {
        // Implementation for creating a new room type
        // Validate input, map to entity, save to database, and return the created RoomTypeVO
        RoomType roomType = RoomType.builder()
                .typeName(request.getTypeName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .capacity(request.getCapacity())
                .status(1) // New room types are enabled by default
                .build();
        roomTypeMapper.insert(roomType);
        return getRoomTypeById(roomType.getId());
    }
    @Override
    public RoomTypeVO getRoomTypeById(Long id) {
        // Implementation for retrieving a room type by its ID
        // Find the entity by ID, handle not found case, and return the RoomTypeVO
        RoomType roomType = roomTypeMapper.selectById(id);
        if (roomType == null) {
            throw new RuntimeException("Room type not found with id: " + id);
        }
        return toVO(roomType);
    }
    @Override
    public RoomTypeVO getRoomTypeByName(String typeName) {
        // Implementation for retrieving a room type by its name
        // Find the entity by name, handle not found case, and return the RoomTypeVO
        RoomType roomType = roomTypeMapper.selectByTypeName(typeName);
        if (roomType == null) {
            throw new RuntimeException("Room type not found with name: " + typeName);
        }
        return toVO(roomType);
    }
    @Override
    public List<RoomTypeVO> getRoomTypes() {
        // Implementation for retrieving all room types
        // Query the database for all room types, map to VO list, and return
        return roomTypeMapper.selectAll()
                .stream()
                .map(this::toVO)
                .toList();
    }
    @Override
    @Transactional
    public RoomTypeVO updateRoomType(Long id, RoomTypeRequest request) {
        // Implementation for updating an existing room type
        // Validate input, find existing entity, update fields, save to database, and return the updated RoomTypeVO
        RoomType existing = roomTypeMapper.selectById(id);

        if (existing == null) {
            throw new BusinessException("Room type does not exist");
        }
        RoomType roomType = RoomType.builder()
                .id(id)
                .typeName(request.getTypeName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .capacity(request.getCapacity())
                .build();

        roomTypeMapper.update(roomType);

        return getRoomTypeById(id);
    }
    @Override
    @Transactional
    public void enableRoomType(Long id) {
        // Implementation for enabling a room type
        // Find the entity by ID, handle not found case, update status to enabled, and save to database
        RoomType existing = roomTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Room type does not exist");
        }
        roomTypeMapper.updateStatus(id, 1);
    }
    @Override
    @Transactional
    public void disableRoomType(Long id) {
        // Implementation for disabling a room type
        // Find the entity by ID, handle not found case, update status to disabled, and save to database
        RoomType existing = roomTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Room type does not exist");
        }
        roomTypeMapper.updateStatus(id, 0);
    }
}
