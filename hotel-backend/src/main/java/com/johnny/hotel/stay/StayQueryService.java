package com.johnny.hotel.stay;

import com.johnny.hotel.pagination.*;
import com.johnny.hotel.entity.StayRoomAssignment;
import com.johnny.hotel.mapper.StayRoomAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class StayQueryService {
    private final StayAccess access;
    private final StayMapper stays;
    private final StayRoomAssignmentMapper assignments;
    private final PaginationSupport pagination;
    public Stay get(Long id){return access.read(id);}
    public PageResult<Stay> page(Long bookingId,Integer status,Integer page,Integer pageSize) {
        var owner=access.customerScope();if(status!=null)try{StayStatus.fromCode(status);}catch(IllegalArgumentException e){throw new com.johnny.hotel.exception.BusinessException("Invalid stay status");}
        var window=pagination.window(page,pageSize,bookingId!=null || status!=null);
        int size=pagination.limit(window);
        return pagination.result(window,size==0?List.of():stays.page(owner,bookingId,status,window.offset(),size),stays.count(owner,bookingId,status));
    }
    public List<StayRoomAssignment> assignments(Long id){access.read(id);return assignments.readByStayId(id);}
}
