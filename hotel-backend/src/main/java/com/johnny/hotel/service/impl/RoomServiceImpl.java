package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.RoomRequest;
import com.johnny.hotel.entity.Room;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.enums.RoomStatus;
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

    private void requireOne(int rows) {
        if (rows != 1) throw new BusinessException("Room was changed concurrently");
    }
    private void requireIdle(Room room) {
        if (!Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new BusinessException("Room maintenance requires a READ_COMMITTED transaction");
        if (room == null) throw new BusinessException("Room does not exist");
        if (room.getStatus() == RoomStatus.BOOKED.getCode() || room.getStatus() == RoomStatus.OCCUPIED.getCode() || roomMapper.hasLiveUse(room.getId()))
            throw new BusinessException("Room belongs to an active reservation or stay");
    }
    private Room lockIdle(Long id) {
        Room room = roomMapper.selectByIdForUpdate(id);
        requireIdle(room);
        return room;
    }
    private void changeIdleStatus(Long id, RoomStatus expected, RoomStatus next) {
        Room room = lockIdle(id);
        if (room.getStatus() != expected.getCode()) throw new BusinessException("Unsupported maintenance transition");
        requireOne(roomMapper.transitionStatus(id, expected.getCode(), next.getCode()));
    }

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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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
                .status(RoomStatus.AVAILABLE.getCode())
                .build();
        roomMapper.insert(room);

        return getRoomById(room.getId());
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RoomVO updateRoom(Long id, RoomRequest request){
        // Implementation for updating an existing room
        // Validate input, find the existing entity, update fields, save to database, and return the updated RoomVO
        Room existing = roomMapper.selectByIdForUpdate(id);

        if (existing == null) {
            throw new BusinessException("Room does not exist");
        }
        requireIdle(existing);
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

        requireOne(roomMapper.update(room));
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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void enableRoom(Long id) {
        changeIdleStatus(id, RoomStatus.DISABLED, RoomStatus.AVAILABLE);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void disableRoom(Long id) {
        Room room = lockIdle(id);
        if (room.getStatus() != RoomStatus.AVAILABLE.getCode() && room.getStatus() != RoomStatus.MAINTENANCE.getCode()) throw new BusinessException("Only available or maintenance rooms can be disabled");
        requireOne(roomMapper.transitionStatus(id, room.getStatus(), RoomStatus.DISABLED.getCode()));
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setRoomMaintenance(Long id) {
        changeIdleStatus(id, RoomStatus.AVAILABLE, RoomStatus.MAINTENANCE);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setRoomBooked(Long id) {
        throw new BusinessException("Use booking approval to reserve a room");
    }
    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setRoomOccupied(Long id) {
        throw new BusinessException("Use booking creation, approval and check-in; bare walk-in occupancy is not supported");
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setRoomAvailable(Long id) {
        if (!Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new BusinessException("Room maintenance requires a READ_COMMITTED transaction");
        Room room=roomMapper.selectByIdForUpdate(id);
        if(room==null||room.getStatus()!=RoomStatus.MAINTENANCE.getCode()||roomMapper.hasActualUse(id))throw new BusinessException("Only an unoccupied maintenance room can be released");
        requireOne(roomMapper.transitionStatus(id,RoomStatus.MAINTENANCE.getCode(),RoomStatus.AVAILABLE.getCode()));
    }

}
