package com.johnny.hotel.service;
import com.johnny.hotel.dto.RoomRequest;
import com.johnny.hotel.dto.RoomTypeRequest;
import com.johnny.hotel.entity.Room;
import com.johnny.hotel.entity.RoomType;
import com.johnny.hotel.vo.RoomTypeVO;
import com.johnny.hotel.vo.RoomVO;

import java.util.List;
public interface RoomService {
    RoomVO createRoom(RoomRequest request);
    RoomVO updateRoom(Long id, RoomRequest request);
    RoomVO getRoomById(Long id);
    RoomVO getRoomByNumber(String roomNumber);
    List<RoomVO> getRooms();
    List<RoomVO> getRoomsByFloor(Integer floor);
    void enableRoom(Long id);
    void disableRoom(Long id);
    void setRoomMaintenance(Long id);
}
