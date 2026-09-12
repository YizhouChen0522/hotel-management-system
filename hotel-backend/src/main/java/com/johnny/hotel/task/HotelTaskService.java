package com.johnny.hotel.task;
import java.util.List;
public interface HotelTaskService {
 HotelTask createGeneral(TaskRequests.CreateGeneral request); HotelTask createTurnover(Long roomId,Long assignmentId,Long actorId);
 List<HotelTask> list(Integer status,Integer type,Integer page,Integer size); TaskView get(Long id); List<TaskAssignment> myTodo();
 TaskView claim(Long id); TaskView accept(Long id); TaskView complete(Long id,String note);
 TaskView assign(Long id,TaskRequests.Assign request); TaskView reassign(Long id,TaskRequests.Assign request); TaskView cancel(Long id,String note); TaskView forceComplete(Long id,String note);
}
