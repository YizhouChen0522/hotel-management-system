package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.RoomRequest;
import com.johnny.hotel.entity.Room;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.RoomMapper;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.service.RoomService;
import com.johnny.hotel.vo.RoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private final RoomMapper roomMapper;
    private final RoomTypeMapper roomTypeMapper;

    private RoomVO toVO(Room room) {
        RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
        return RoomVO.builder()
                .id(room.getId())
                .roomNumber(room.getRoomNumber())
                .roomTypeId(room.getRoomTypeId())
                .roomTypeName(roomType == null ? null : roomType.getTypeName())
                .floor(room.getFloor())
                .status(room.getStatus())
                .createTime(room.getCreateTime())
                .updateTime(room.getUpdateTime())
                .build();
    }

    @Override
    @Transactional
    public RoomVO createRoom(RoomRequest request) {
        // Implementation for creating a new room
        // Validate input, map to entity, save to database, and return the created RoomVO
        if (roomMapper.selectByRoomNumber(request.getRoomNumber()) != null) {
            throw new BusinessException("Room number already exists");
        }
        RoomType roomType = roomTypeMapper.selectById(request.getRoomTypeId());

        if (roomType == null) {
            throw new BusinessException("Room type does not exist");
        }
        Room room = Room.builder()
                .roomNumber(request.getRoomNumber())
                .roomTypeId(request.getRoomTypeId())
                .floor(request.getFloor())
                .status(1)
                .build();
        roomMapper.insert(room);

        return getRoomById(room.getId());
    }

    @Override
    @Transactional
    public RoomVO updateRoom(Long id, RoomRequest request){
        // Implementation for updating an existing room
        // Validate input, find the existing entity, update fields, save to database, and return the updated RoomVO
        Room existing = roomMapper.selectById(id);

        if (existing == null) {
            throw new BusinessException("Room does not exist");
        }
        Room duplicate = roomMapper.selectByRoomNumber(request.getRoomNumber());

        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new BusinessException("Room number already exists");
        }
        RoomType roomType = roomTypeMapper.selectById(request.getRoomTypeId());

        if (roomType == null) {
            throw new BusinessException("Room type does not exist");
        }
        Room room = Room.builder()
                .id(id)
                .roomNumber(request.getRoomNumber())
                .roomTypeId(request.getRoomTypeId())
                .floor(request.getFloor())
                .build();

        roomMapper.update(room);
        return getRoomById(id);

    }
    @Override
    public RoomVO getRoomById(Long id) {
        // Implementation for retrieving a room by its ID
        // Find the entity by ID, handle not found case, and return the RoomVO
        Room room = roomMapper.selectById(id);
        if (room == null) {
            throw new BusinessException("Room not found with id: " + id);
        }
        return toVO(room);
    }
    @Override
    public RoomVO getRoomByNumber(String roomNumber) {
        // Implementation for retrieving a room by its number
        // Find the entity by room number, handle not found case, and return the RoomVO
        Room room = roomMapper.selectByRoomNumber(roomNumber);
        if (room == null) {
            throw new BusinessException("Room not found with number: " + roomNumber);
        }
        return toVO(room);
    }

    @Override
    public List<RoomVO> getRooms() {
        return roomMapper.selectAll()
                .stream()
                .map(this::toVO)
                .toList();
    }


    @Override
    public List<RoomVO> getRoomsByFloor(Integer floor) {
        List<Room> rooms = roomMapper.selectByFloor(floor);
        return rooms.stream()
                .map(this::toVO)
                .toList();
    }
    @Override
    @Transactional
    public void enableRoom(Long id) {
        if (roomMapper.selectById(id) == null) {
            throw new BusinessException("Room does not exist");
        }

        roomMapper.updateStatus(id, 1);
    }
    @Override
    @Transactional
    public void disableRoom(Long id) {
        if (roomMapper.selectById(id) == null) {
            throw new BusinessException("Room does not exist");
        }

        roomMapper.updateStatus(id, 0);
    }
}
