package com.johnny.hotel.service;

import com.johnny.hotel.vo.StayHistoryVO;

import java.util.List;

public interface StayHistoryService {
    void createForCompletedStay(Long bookingId);
    List<StayHistoryVO> getByUserId(Long userId);
    List<StayHistoryVO> getByFolioIdAndUserId(Long folioId, Long userId);
}
