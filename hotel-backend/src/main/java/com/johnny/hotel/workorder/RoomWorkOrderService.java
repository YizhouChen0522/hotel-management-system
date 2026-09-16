package com.johnny.hotel.workorder;
import com.johnny.hotel.task.TaskView;import java.util.List;
public interface RoomWorkOrderService {
 RoomWorkOrder report(WorkOrderRequests.Report request);RoomWorkOrder get(Long id);List<RoomWorkOrder> list(Integer status,Long roomId,Integer page,Integer size);
 com.johnny.hotel.pagination.PageResult<RoomWorkOrder> page(Integer status,Long roomId,Integer page,Integer size);
 TaskView task(Long id);RoomWorkOrder complete(Long id,WorkOrderRequests.Complete request,boolean force);RoomWorkOrder cancel(Long id,String note);
}
