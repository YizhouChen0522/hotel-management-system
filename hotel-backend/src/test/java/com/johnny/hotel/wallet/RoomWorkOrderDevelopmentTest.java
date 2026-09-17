package com.johnny.hotel.wallet;
import com.johnny.hotel.workorder.*;import com.johnny.hotel.task.*;import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.security.access.AccessDeniedException;
import java.math.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class RoomWorkOrderDevelopmentTest extends FinancialDevelopmentFixture {
 @Autowired RoomWorkOrderService workOrders;@Autowired HotelTaskService tasks;@Autowired TodoService todoService;@Autowired org.flywaydb.core.Flyway flyway;int keys;
 WorkOrderRequests.Report report(long room,boolean blocking){return WorkOrderRequests.Report.builder().roomId(room).requestKey("work_"+keys++).damageType("AIR_CONDITIONING").description("Air conditioning compressor requires repair").severity(WorkOrderSeverity.HIGH).blocksRoomRelease(blocking).estimatedCost(new BigDecimal("120.50")).build();}
 RoomWorkOrder create(String role,long room,boolean blocking){as(role);return workOrders.report(report(room,blocking));}
 long todo(long task){return todoService.mine(1,100).stream().filter(t->t.getTaskId().equals(task)).findFirst().orElseThrow().getId();}
 int roomStatus(long id){return jdbc.queryForObject("SELECT status FROM room WHERE id=?",Integer.class,id);}
 int taskStatus(long id){return jdbc.queryForObject("SELECT status FROM hotel_task WHERE id=?",Integer.class,id);}
 int records(long id,int type){return jdbc.queryForObject("SELECT COUNT(*) FROM task_record WHERE task_id=? AND record_type=?",Integer.class,id,type);}
 TaskRequests.Assign assign(Long... ids){return TaskRequests.Assign.builder().assigneeUserIds(List.of(ids)).note("Maintenance assignment").build();}
 @Test void migrationAndSchemaConstraints(){flyway.validate();assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='18'",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='19'",Integer.class));assertEquals(3,TaskType.ROOM_MAINTENANCE.getCode());assertEquals(3,WorkOrderSeverity.CRITICAL.getCode());}
 @Test void staffReportCreatesOpenPersonalTaskWithoutTouchingRoom(){
  var w=create("STAFF",room1,true);assertEquals(0,w.getStatus());assertEquals(uid("STAFF"),w.getReportedBy());assertEquals(1,roomStatus(room1));
  var t=tasks.get(w.getTaskId()).getTask();assertEquals(3,t.getTaskType());assertEquals(0,t.getStatus());assertEquals(0,t.getExecutionType());assertEquals(1,records(t.getId(),0));
 }
 @Test void nonBlockingDamageDoesNotChangeRoom(){var w=create("STAFF",room1,false);assertEquals(1,roomStatus(room1));assertFalse(w.getBlocksRoomRelease());}
 @Test void reportRetryIsIdempotentAndDifferentPayloadRejected(){
  as("STAFF");var r=report(room1,true);var w=workOrders.report(r);assertEquals(w.getId(),workOrders.report(r).getId());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM room_work_order WHERE reported_by=? AND request_key=?",Integer.class,uid("STAFF"),r.getRequestKey()));
  r.setDescription("Different damage");assertThrows(BusinessException.class,()->workOrders.report(r));
 }
 @ParameterizedTest @ValueSource(strings={"CUSTOMER","HR_ADMIN"})
 void customerAndHrCannotAccessServiceOrHttp(String role)throws Exception{
  as(role);assertThrows(AccessDeniedException.class,()->workOrders.report(report(room1,true)));assertThrows(AccessDeniedException.class,()->workOrders.list(null,null,1,50));
  String body="{\"roomId\":"+room1+",\"requestKey\":\"denied_work\",\"damageType\":\"LOCK\",\"description\":\"Door lock damaged\",\"severity\":\"HIGH\",\"blocksRoomRelease\":true}";
  mvc.perform(get("/api/work-orders").with(authentication(auth(role)))).andExpect(status().isForbidden());mvc.perform(post("/api/work-orders").with(authentication(auth(role))).contentType("application/json").content(body)).andExpect(status().isForbidden());
 }
 @Test void anonymousIs401()throws Exception{mvc.perform(get("/api/work-orders").with(anonymous())).andExpect(status().isUnauthorized());}
 @Test void claimTodoLifecycleAndWorkOrderCompletionAreAtomic(){
  var w=create("STAFF",room1,true);tasks.claim(w.getTaskId());long todo=todo(w.getTaskId());assertEquals(0,todoService.get(todo).getStatus());
  todoService.acknowledge(todo,null);todoService.block(todo,"Waiting for part");assertEquals(4,taskStatus(w.getTaskId()));todoService.resume(todo,null);assertEquals(1,taskStatus(w.getTaskId()));
  assertThrows(BusinessException.class,()->todoService.complete(todo,null));assertThrows(BusinessException.class,()->tasks.complete(w.getTaskId(),null));
  var done=workOrders.complete(w.getId(),WorkOrderRequests.Complete.builder().actualCost(new BigDecimal("99.25")).note("Repaired").build(),false);
  assertEquals(1,done.getStatus());assertEquals(new BigDecimal("99.25"),done.getActualCost());assertEquals(2,taskStatus(w.getTaskId()));assertEquals(2,todoService.get(todo).getStatus());assertEquals(1,roomStatus(room1));
 }
 @Test void managerAssignProducesTodoAndCanForceComplete(){
  var w=create("MANAGER",room1,true);tasks.assign(w.getTaskId(),assign(uid("STAFF")));as("STAFF");assertEquals(0,todoService.get(todo(w.getTaskId())).getStatus());
  as("OWNER");workOrders.complete(w.getId(),WorkOrderRequests.Complete.builder().actualCost(BigDecimal.TEN).build(),true);assertEquals(1,workOrders.get(w.getId()).getStatus());assertEquals(2,taskStatus(w.getTaskId()));
 }
 @Test void managerCanPromoteMaintenanceToShared(){
  var w=create("MANAGER",room1,false);tasks.assign(w.getTaskId(),assign(uid("STAFF"),uid("OWNER")));var t=tasks.get(w.getTaskId()).getTask();assertEquals(1,t.getExecutionType());assertEquals(2,t.getAssignmentMode());
  as("STAFF");todoService.acknowledge(todo(w.getTaskId()),null);workOrders.complete(w.getId(),null,false);assertEquals(1,workOrders.get(w.getId()).getStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND status<>2",Integer.class,w.getTaskId()));
 }
 @Test void staffCannotCancelOrForceComplete(){
  var w=create("STAFF",room1,true);assertThrows(AccessDeniedException.class,()->workOrders.cancel(w.getId(),null));assertThrows(AccessDeniedException.class,()->workOrders.complete(w.getId(),null,true));assertEquals(0,workOrders.get(w.getId()).getStatus());
 }
 @ParameterizedTest @ValueSource(strings={"MANAGER","OWNER","SUPER_ADMIN"})
 void managersCancelWithoutReleasingRoom(String role){
  var w=create("STAFF",room1,true);as(role);var cancelled=workOrders.cancel(w.getId(),"Duplicate report");assertEquals(2,cancelled.getStatus());assertEquals(3,taskStatus(w.getTaskId()));assertEquals(1,roomStatus(room1));assertEquals(2,workOrders.cancel(w.getId(),null).getStatus());
 }
 @Test void occupiedRoomReportPreservesBookingAssignmentAndLaterCheckoutMaintenance(){
  long b=stay();long assignment=jdbc.queryForObject("SELECT id FROM stay_room_assignment WHERE stay_id=(SELECT id FROM stay WHERE booking_id=?) AND end_time IS NULL",Long.class,b);var w=create("STAFF",room1,true);
  assertEquals(4,roomStatus(room1));assertEquals(1,stayState(b));assertNull(jdbc.queryForObject("SELECT end_time FROM stay_room_assignment WHERE id=?",java.time.LocalDateTime.class,assignment));
  pay(b,"300");clock.day(3);checkout(b);assertEquals(3,roomStatus(room1));assertEquals(2,stayState(b));assertEquals(0,workOrders.get(w.getId()).getStatus());
 }
 @Test void bookedRoomReportPreservesReservationLifecycle(){
  long b=createBooking("CUSTOMER");var approve=new com.johnny.hotel.dto.ApproveBookingRequest();approve.setAssignedRoomId(room1);bookings.approveBooking(b,approve,uid("MANAGER"));var w=create("STAFF",room1,true);
  assertEquals(2,roomStatus(room1));assertEquals(1,bookingState(b));assertEquals(0,w.getStatus());
 }
 @ParameterizedTest @ValueSource(strings={"HotelTaskMapper.insert","TaskRecordMapper.insert","RoomWorkOrderMapper.insert","SysAuditLogMapper.insert"})
 void reportFailureRollsBackTaskWorkOrderAndRoom(String statement){
  as("STAFF");var r=report(room1,true);gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->workOrders.report(r));
  assertEquals(1,roomStatus(room1));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM room_work_order WHERE request_key=?",Integer.class,r.getRequestKey()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_task WHERE source_key=?",Integer.class,"ROOM_MAINTENANCE:"+uid("STAFF")+":"+r.getRequestKey()));
 }
 @ParameterizedTest @ValueSource(strings={"TodoMapper.transition","HotelTaskMapper.transition","RoomWorkOrderMapper.resolve","SysAuditLogMapper.insert"})
 void completionFailureRollsBackEveryState(String statement){
  var w=create("STAFF",room1,true);tasks.claim(w.getTaskId());long todo=todo(w.getTaskId());todoService.acknowledge(todo,null);int records=jdbc.queryForObject("SELECT COUNT(*) FROM task_record WHERE task_id=?",Integer.class,w.getTaskId());
  gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->workOrders.complete(w.getId(),WorkOrderRequests.Complete.builder().actualCost(BigDecimal.ONE).build(),false));
  assertEquals(0,workOrders.get(w.getId()).getStatus());assertEquals(1,taskStatus(w.getTaskId()));assertEquals(1,todoService.get(todo).getStatus());assertEquals(records,jdbc.queryForObject("SELECT COUNT(*) FROM task_record WHERE task_id=?",Integer.class,w.getTaskId()));
 }
 @Test void concurrentClaimStillProducesOneResponsibleTodo(){
  var w=create("MANAGER",room1,false);assertInstanceOf(BusinessException.class,serialized("HotelTaskMapper.transition",()->{as("STAFF");tasks.claim(w.getTaskId());},()->{as("OWNER");tasks.claim(w.getTaskId());}));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=?",Integer.class,w.getTaskId()));
 }
 @Test void concurrentCompletionIsIdempotentAndDoesNotDuplicateResolution(){
  var w=create("STAFF",room1,true);tasks.claim(w.getTaskId());long todo=todo(w.getTaskId());todoService.acknowledge(todo,null);
  assertNull(serialized("RoomWorkOrderMapper.resolve",()->{as("STAFF");workOrders.complete(w.getId(),null,false);},()->{as("STAFF");workOrders.complete(w.getId(),null,false);}));
  assertEquals(1,workOrders.get(w.getId()).getStatus());assertEquals(1,records(w.getTaskId(),12));assertEquals(2,taskStatus(w.getTaskId()));
 }
 @Test void actualCostDoesNotCreateFolioCharge(){
  long b=stay();int before=jdbc.queryForObject("SELECT COUNT(*) FROM folio_item WHERE folio_id=?",Integer.class,folio(b));var w=create("STAFF",room1,false);tasks.claim(w.getTaskId());todoService.acknowledge(todo(w.getTaskId()),null);workOrders.complete(w.getId(),WorkOrderRequests.Complete.builder().actualCost(new BigDecimal("500.00")).build(),false);
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM folio_item WHERE folio_id=?",Integer.class,folio(b)));
 }
 @Test void openBlockingOrderBlocksManualReleaseUntilResolved(){
  var w=create("STAFF",room1,true);assertEquals(1,roomStatus(room1));rooms.setRoomMaintenance(room1);assertEquals(3,roomStatus(room1));
  assertThrows(BusinessException.class,()->rooms.setRoomAvailable(room1));tasks.claim(w.getTaskId());todoService.acknowledge(todo(w.getTaskId()),null);
  workOrders.complete(w.getId(),null,false);rooms.setRoomAvailable(room1);assertEquals(1,roomStatus(room1));
 }
 @Test void cancelledBlockingOrderNoLongerBlocksRelease(){
  var w=create("STAFF",room1,true);rooms.setRoomMaintenance(room1);assertThrows(BusinessException.class,()->rooms.setRoomAvailable(room1));
  as("MANAGER");workOrders.cancel(w.getId(),"Duplicate report");rooms.setRoomAvailable(room1);assertEquals(1,roomStatus(room1));
 }
 @Test void openNonBlockingOrderDoesNotBlockRelease(){
  var w=create("STAFF",room1,false);assertTrue(w.getBlocksRoomRelease()==Boolean.FALSE);rooms.setRoomMaintenance(room1);
  rooms.setRoomAvailable(room1);assertEquals(1,roomStatus(room1));assertEquals(0,workOrders.get(w.getId()).getStatus());
 }
 @Test void reportingAgainstMaintenanceRoomKeepsMaintenance(){
  rooms.setRoomMaintenance(room1);var w=create("STAFF",room1,true);assertEquals(3,roomStatus(room1));assertEquals(0,w.getStatus());
 }
 @Test void completionAndCancellationNeverAutoReleaseRoom(){
  var done=create("STAFF",room1,true);rooms.setRoomMaintenance(room1);tasks.claim(done.getTaskId());todoService.acknowledge(todo(done.getTaskId()),null);
  workOrders.complete(done.getId(),null,false);assertEquals(1,workOrders.get(done.getId()).getStatus());assertEquals(3,roomStatus(room1));
  var cancelled=create("STAFF",room1,true);as("MANAGER");workOrders.cancel(cancelled.getId(),null);assertEquals(2,workOrders.get(cancelled.getId()).getStatus());assertEquals(3,roomStatus(room1));
  rooms.setRoomAvailable(room1);assertEquals(1,roomStatus(room1));
 }
 @Test void disabledReporterRejectedAtService(){jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",uid("STAFF"));as("STAFF");assertThrows(AccessDeniedException.class,()->workOrders.report(report(room1,true)));}
 @Test void httpReportAndManagerOperationsUseJwt()throws Exception{
  String body="{\"roomId\":"+room1+",\"requestKey\":\"http_work\",\"damageType\":\"PLUMBING\",\"description\":\"Pipe leak\",\"severity\":\"CRITICAL\",\"blocksRoomRelease\":true}";
  mvc.perform(post("/api/work-orders").with(authentication(auth("STAFF"))).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(0));
  long id=jdbc.queryForObject("SELECT id FROM room_work_order WHERE request_key='http_work' AND reported_by=?",Long.class,uid("STAFF"));
  mvc.perform(post("/api/work-orders/"+id+"/cancel").with(authentication(auth("STAFF")))).andExpect(status().isForbidden());mvc.perform(post("/api/work-orders/"+id+"/cancel").with(authentication(auth("MANAGER")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(2));
 }
}
