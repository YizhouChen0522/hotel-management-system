package com.johnny.hotel.service;

import com.johnny.hotel.vo.RoomTypeVO;
import com.johnny.hotel.dto.RoomTypeRequest;

import java.util.List;
public interface RoomTypeService {
    RoomTypeVO createRoomType(RoomTypeRequest request);
    RoomTypeVO updateRoomType(Long id, RoomTypeRequest request);
    RoomTypeVO getRoomTypeById(Long id);
    RoomTypeVO getRoomTypeByName(String typeName);
    List<RoomTypeVO> getRoomTypes();
    void enableRoomType(Long id);
    void disableRoomType(Long id);

}
