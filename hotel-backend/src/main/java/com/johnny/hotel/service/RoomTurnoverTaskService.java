package com.johnny.hotel.service;
import com.johnny.hotel.entity.RoomTurnoverTask;
import java.util.List;
public interface RoomTurnoverTaskService {
    /** Internal lifecycle operation: caller must already own its lifecycle transaction. */
    RoomTurnoverTask createForClosedAssignment(Long assignmentId);
    RoomTurnoverTask createForClosedAssignment(Long assignmentId,Long actorId);
    List<RoomTurnoverTask> list(Integer status,Long roomId,Integer page,Integer size);
    RoomTurnoverTask get(Long id);
    RoomTurnoverTask accept(Long id);
    RoomTurnoverTask complete(Long id,String note);
}
