package com.johnny.hotel.service;

import com.johnny.hotel.vo.StayHistoryVO;

import java.util.List;

public interface StayHistoryService {
    void createForCompletedStay(Long bookingId);
    List<StayHistoryVO> getByUserId(Long userId);
    List<StayHistoryVO> getByFolioIdAndUserId(Long folioId, Long userId);
    com.johnny.hotel.pagination.PageResult<StayHistoryVO> pageByUser(Long userId,Integer page,Integer pageSize);
    com.johnny.hotel.pagination.PageResult<StayHistoryVO> pageByFolioUser(Long folioId,Long userId,Integer page,Integer pageSize);
}
