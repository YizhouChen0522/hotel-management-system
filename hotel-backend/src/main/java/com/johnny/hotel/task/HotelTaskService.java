package com.johnny.hotel.task;
import java.util.List;
public interface HotelTaskService {
 HotelTask createGeneral(TaskRequests.CreateGeneral request); HotelTask createTurnover(Long roomId,Long assignmentId,Long actorId);
 List<HotelTask> list(Integer status,Integer type,Integer page,Integer size); TaskView get(Long id); List<TaskAssignment> myTodo();
 TaskView claim(Long id); TaskView accept(Long id); TaskView complete(Long id,String note);
 TaskView assign(Long id,TaskRequests.Assign request); TaskView reassign(Long id,TaskRequests.Assign request); TaskView cancel(Long id,String note); TaskView forceComplete(Long id,String note);
 List<Todo> todos(Integer page,Integer size); Todo todo(Long id); Todo updateTodo(Long id,String action,String note);
 TaskView addAssignees(Long id,TaskRequests.Assign request); HotelTask subtask(Long id,TaskRequests.CreateGeneral request);
 HotelTask createMaintenance(Long roomId,String title,String description,String requestKey,Long actorId);
 TaskView completeMaintenance(Long taskId,String note,boolean force); TaskView cancelMaintenance(Long taskId,String note);
 HotelTask createCleaning(Long bookingId,Long roomId,Long assignmentId,String sourceKey,String description,Long actorId);
 HotelTask createGuestService(Long bookingId,String sourceKey,String title,String description,Long actorId);
 HotelTask createRepair(Long roomId,String sourceKey,String description,Long actorId);
 TaskView completeCleaning(Long taskId,String note,boolean force); TaskView cancelCleaning(Long taskId,String note);
 TaskView completeGuestService(Long taskId,String note,boolean force); TaskView cancelGuestService(Long taskId,String note);
 TaskView claimGuestService(Long taskId);
 com.johnny.hotel.pagination.PageResult<TaskRecord> records(Long taskId,Integer recordType,Long actorId,Integer page,Integer pageSize);
}
