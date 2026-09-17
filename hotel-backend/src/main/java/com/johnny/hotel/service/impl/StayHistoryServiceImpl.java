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
    private final com.johnny.hotel.service.support.BillingAccess access;
    private final com.johnny.hotel.pagination.PaginationSupport pagination;

    @Override
    @Transactional
    public void createForCompletedStay(Long stayId) {

        List<StayHistory> snapshots =
                stayHistoryMapper.findCompletedAssignmentSnapshots(stayId);

        for (StayHistory snapshot : snapshots) {

            if (stayHistoryMapper.countByAssignmentId(
                    snapshot.getAssignmentId()) > 0) {
                continue;
            }

            stayHistoryMapper.insert(
                    StayHistory.builder()
                            .stayId(snapshot.getStayId())
                            .folioId(snapshot.getFolioId())
                            .assignmentId(snapshot.getAssignmentId())
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
        access.currentCustomer(userId);
        return stayHistoryMapper
                .findByFolioIdAndUserId(folioId, userId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<StayHistoryVO> getByUserId(Long userId) {
        access.currentCustomer(userId);
        return stayHistoryMapper.findByUserId(userId)
                .stream()
                .map(this::toVO)
                .toList();
    }
    public com.johnny.hotel.pagination.PageResult<StayHistoryVO> pageByUser(Long userId,Integer page,Integer size){access.currentCustomer(userId);var w=pagination.window(page,size,true);var rows=stayHistoryMapper.pageByUser(userId,w.offset(),pagination.limit(w)).stream().map(this::toVO).toList();return pagination.result(w,rows,stayHistoryMapper.countByUser(userId));}
    public com.johnny.hotel.pagination.PageResult<StayHistoryVO> pageByFolioUser(Long folio,Long user,Integer page,Integer size){access.currentCustomer(user);var w=pagination.window(page,size,true);var rows=stayHistoryMapper.pageByFolioUser(folio,user,w.offset(),pagination.limit(w)).stream().map(this::toVO).toList();return pagination.result(w,rows,stayHistoryMapper.countByFolioUser(folio,user));}

    private StayHistoryVO toVO(StayHistory stayHistory) {
        return StayHistoryVO.builder()
                .id(stayHistory.getId())
                .stayId(stayHistory.getStayId())
                .folioId(stayHistory.getFolioId())
                .actualCheckInTime(stayHistory.getActualCheckInTime())
                .actualCheckOutTime(stayHistory.getActualCheckOutTime())
                .roomNumber(stayHistory.getRoomNumber())
                .roomTypeName(stayHistory.getRoomTypeName())
                .build();
    }
}
