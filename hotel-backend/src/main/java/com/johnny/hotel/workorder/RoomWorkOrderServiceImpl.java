package com.johnny.hotel.workorder;
import com.johnny.hotel.entity.*;import com.johnny.hotel.enums.RoomStatus;import com.johnny.hotel.exception.BusinessException;import com.johnny.hotel.mapper.*;import com.johnny.hotel.task.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;import java.math.*;import java.util.*;
@Service @RequiredArgsConstructor public class RoomWorkOrderServiceImpl implements RoomWorkOrderService {
 private final RoomWorkOrderMapper orders;private final RoomMapper rooms;private final HotelTaskService tasks;private final WorkOrderAccess access;private final SysAuditLogMapper audits;private final com.johnny.hotel.pagination.PaginationSupport pagination;
 private void require(boolean ok,String message){if(!ok)throw new BusinessException(message);}private void one(int rows){if(rows!=1)throw new BusinessException(409,"WorkOrder state changed");}
 private String text(String value,int max,String field){String v=value==null?null:value.trim();require(v!=null&&!v.isEmpty(),field+" required");require(v.length()<=max,field+" too long");return v;}
 private int[] page(Integer page,Integer size){int p=page==null?1:page,s=size==null?50:size;require(p>0&&s>0&&s<=100&&(long)(p-1)*s<=Integer.MAX_VALUE,"Invalid pagination");return new int[]{(p-1)*s,s};}
 private RoomWorkOrder found(Long id){var w=orders.find(id);if(w==null)throw new BusinessException(404,"WorkOrder not found");return w;}
 private void audit(Long actor,Long id,String action){one(audits.insert(SysAuditLog.builder().operatorId(actor).action(action).detail("Room WorkOrder "+id).build()));}
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public RoomWorkOrder report(WorkOrderRequests.Report r){
  var a=access.actor();require(r!=null&&r.getRoomId()!=null&&r.getSeverity()!=null&&r.getBlocksRoomRelease()!=null,"WorkOrder fields required");
  String key=text(r.getRequestKey(),63,"Request key"),damage=text(r.getDamageType(),80,"Damage type"),description=text(r.getDescription(),500,"Description");
  BigDecimal estimated=money(r.getEstimatedCost());var old=orders.byRequest(a.id(),key);
  if(old!=null){require(old.getRoomId().equals(r.getRoomId())&&old.getDamageType().equals(damage)&&old.getDescription().equals(description)&&old.getSeverity()==r.getSeverity().getCode()&&old.getBlocksRoomRelease().equals(r.getBlocksRoomRelease())&&Objects.equals(old.getEstimatedCost(),estimated),"Request key already represents another WorkOrder");return old;}
  var room=rooms.selectByIdForUpdate(r.getRoomId());require(room!=null,"Room not found");
  String source="ROOM_MAINTENANCE:"+a.id()+":"+key;var task=tasks.createMaintenance(room.getId(),"Repair room "+room.getRoomNumber()+": "+damage,description,source,a.id());
  var w=RoomWorkOrder.builder().taskId(task.getId()).roomId(room.getId()).reportedBy(a.id()).requestKey(key).damageType(damage).description(description).severity(r.getSeverity().getCode()).affectsSellability(r.getBlocksRoomRelease()).blocksRoomRelease(r.getBlocksRoomRelease()).estimatedCost(estimated).status(0).build();
  one(orders.insert(w));
  if(Boolean.TRUE.equals(r.getBlocksRoomRelease())&&
          (room.getStatus()==RoomStatus.AVAILABLE.getCode()||room.getStatus()==RoomStatus.BOOKED.getCode())){
   one(rooms.transitionStatus(room.getId(),room.getStatus(),RoomStatus.MAINTENANCE.getCode()));
  }
  audit(a.id(),w.getId(),"REPORT_ROOM_WORK_ORDER");return orders.find(w.getId());
 }
 private BigDecimal money(BigDecimal value){if(value==null)return null;try{var v=value.setScale(2,RoundingMode.UNNECESSARY);require(v.signum()>=0&&v.precision()<=12,"Invalid cost");return v;}catch(ArithmeticException e){throw new BusinessException("Cost supports at most two decimals");}}
 @Override public RoomWorkOrder get(Long id){access.actor();return found(id);}
 @Override public List<RoomWorkOrder> list(Integer status,Long roomId,Integer page,Integer size){return page(status,roomId,page,size).getItems();}
 @Override public com.johnny.hotel.pagination.PageResult<RoomWorkOrder> page(Integer status,Long roomId,Integer page,Integer size){access.actor();if(status!=null)require(status>=0&&status<=2,"Invalid status");boolean search=status!=null||roomId!=null;var w=pagination.window(page,size,search);int limit=pagination.limit(w);var rows=limit==0?List.<RoomWorkOrder>of():orders.page(status,roomId,w.offset(),limit);return pagination.result(w,rows,orders.count(status,roomId));}
 @Override public TaskView task(Long id){var w=get(id);return tasks.get(w.getTaskId());}
 @Override @Transactional public RoomWorkOrder complete(Long id,WorkOrderRequests.Complete r,boolean force){
  var a=access.actor();if(force)access.manager(a);var w=orders.lock(id);require(w!=null,"WorkOrder not found");if(w.getStatus()==1)return w;require(w.getStatus()==0,"Cancelled WorkOrder cannot be completed");
  BigDecimal cost=money(r==null?null:r.getActualCost());String note=r==null?null:r.getNote();tasks.completeMaintenance(w.getTaskId(),note,force);one(orders.resolve(id,cost));audit(a.id(),id,force?"FORCE_COMPLETE_ROOM_WORK_ORDER":"RESOLVE_ROOM_WORK_ORDER");return orders.find(id);
 }
 @Override @Transactional public RoomWorkOrder cancel(Long id,String note){
  var a=access.actor();access.manager(a);var w=orders.lock(id);require(w!=null,"WorkOrder not found");if(w.getStatus()==2)return w;require(w.getStatus()==0,"Resolved WorkOrder cannot be cancelled");
  tasks.cancelMaintenance(w.getTaskId(),note);one(orders.cancel(id));audit(a.id(),id,"CANCEL_ROOM_WORK_ORDER");return orders.find(id);
 }
}
