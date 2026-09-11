package com.johnny.hotel.service.impl;

import com.johnny.hotel.entity.StayHistory;
import com.johnny.hotel.mapper.StayHistoryMapper;
import com.johnny.hotel.service.StayHistoryService;
import com.johnny.hotel.vo.StayHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StayHistoryServiceImpl implements StayHistoryService {

    private final StayHistoryMapper stayHistoryMapper;

    @Override
    @Transactional
    public void createForCompletedStay(Long bookingId) {

        List<StayHistory> snapshots =
                stayHistoryMapper.findCompletedAssignmentSnapshots(bookingId);

        for (StayHistory snapshot : snapshots) {

            if (stayHistoryMapper.countByAssignmentId(
                    snapshot.getAssignmentId()) > 0) {
                continue;
            }

            stayHistoryMapper.insert(
                    StayHistory.builder()
                            .userId(snapshot.getUserId())
                            .folioId(snapshot.getFolioId())
                            .assignmentId(snapshot.getAssignmentId())
                            .actualCheckInTime(snapshot.getActualCheckInTime())
                            .actualCheckOutTime(snapshot.getActualCheckOutTime())
                            .roomNumber(snapshot.getRoomNumber())
                            .roomTypeName(snapshot.getRoomTypeName())
                            .build()
            );
        }
    }


    @Override
    public List<StayHistoryVO> getByFolioIdAndUserId(
            Long folioId,
            Long userId) {

        return stayHistoryMapper
                .findByFolioIdAndUserId(folioId, userId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<StayHistoryVO> getByUserId(Long userId) {
        return stayHistoryMapper.findByUserId(userId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    private StayHistoryVO toVO(StayHistory stayHistory) {
        return StayHistoryVO.builder()
                .id(stayHistory.getId())
                .userId(stayHistory.getUserId())
                .folioId(stayHistory.getFolioId())
                .actualCheckInTime(stayHistory.getActualCheckInTime())
                .actualCheckOutTime(stayHistory.getActualCheckOutTime())
                .roomNumber(stayHistory.getRoomNumber())
                .roomTypeName(stayHistory.getRoomTypeName())
                .build();
    }
}
