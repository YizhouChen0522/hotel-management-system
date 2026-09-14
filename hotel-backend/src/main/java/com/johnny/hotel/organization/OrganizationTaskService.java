package com.johnny.hotel.organization;
import com.johnny.hotel.task.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import com.johnny.hotel.exception.BusinessException;
import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class OrganizationTaskService {
 private final HotelTaskMapper tasks;private final TaskAssignmentMapper assignments;private final TodoMapper todos;private final TaskRecordMapper records;private final OrganizationMapper organization;private final OrganizationAccess access;private final Clock clock;
 private void one(int rows){if(rows!=1)throw new BusinessException(409,"Organization task changed");}
 private LocalDateTime now(){return LocalDateTime.now(clock);}
 private void record(Long task,Long assignment,TaskRecordType type,Long actor,String detail){one(records.insert(TaskRecord.builder().taskId(task).assignmentId(assignment).recordType(type.getCode()).actorUserId(actor).detail(detail).build()));}
 public Long create(OrganizationChangeRequest r){
  var t=HotelTask.builder().taskType(2).executionType(0).status(0).assignmentMode(0).title("Organization approval").description(r.getReason()).createdBy(r.getRequestedBy()).build();one(tasks.insert(t));
  record(t.getId(),null,TaskRecordType.TASK_CREATED,r.getRequestedBy(),"Organization approval request");
  Long hr=null;
  if((r.getRequestType()==3||r.getRequestType()==4)&&access.employee(r.getRequestedBy()).has("HR_ADMIN"))hr=r.getRequestedBy();
  else for(Long id:organization.hrs())if(!id.equals(r.getRequestedBy())){try{access.hr(access.employee(id));hr=id;break;}catch(org.springframework.security.access.AccessDeniedException ignored){}}
  if(hr!=null)assign(t,hr,r.getRequestedBy());
  return t.getId();
 }
 private void assign(HotelTask t,Long hr,Long actor){
  var a=TaskAssignment.builder().taskId(t.getId()).assigneeUserId(hr).assignedBy(actor).status(0).isCurrent(1).assignmentRound(assignments.maxRound(t.getId())+1).build();one(assignments.insert(a));
  var todo=Todo.builder().taskId(t.getId()).assignmentId(a.getId()).userId(hr).build();one(todos.insert(todo));
  one(tasks.resetAssignment(t.getId(),1,1));
  record(t.getId(),a.getId(),TaskRecordType.TASK_ASSIGNED,actor,"HR approval responsibility");
  record(t.getId(),a.getId(),TaskRecordType.TODO_CREATED,actor,"Todo "+todo.getId());
 }
 public void claim(OrganizationChangeRequest r,Long actor){
  var t=tasks.lock(r.getTaskId());if(t==null||t.getTaskType()!=2||t.getStatus()==2||t.getStatus()==3)throw new BusinessException(409,"Approval task is not open");
  var rows=assignments.byTask(t.getId()).stream().filter(a->a.getIsCurrent()==1).toList();
  if(rows.stream().anyMatch(a->a.getAssigneeUserId().equals(actor)))return;
  // Any eligible HR may take over an approval; old responsibility and Todo remain historical.
  for(var row:rows)record(t.getId(),row.getId(),TaskRecordType.TODO_RETIRED,actor,"HR approval reassigned");
  todos.retire(t.getId(),now());assignments.endCurrent(t.getId(),now());
  assign(t,actor,actor);record(t.getId(),null,TaskRecordType.TASK_CLAIMED,actor,"Approval claimed by HR");
 }
 public void finish(OrganizationChangeRequest r,Long actor,boolean cancelled){
  var persisted=organization.request(r.getId());
  if(persisted==null||persisted.getStatus()==0||!persisted.getTaskId().equals(r.getTaskId())||(cancelled!=(persisted.getStatus()==3)))throw new BusinessException("Resolve the organization request before completing its task");
  var t=tasks.lock(r.getTaskId());
  if(t==null||t.getTaskType()!=2)throw new BusinessException("Organization task missing");
  if(t.getStatus()==2||t.getStatus()==3){if(t.getStatus()!=(cancelled?3:2))throw new BusinessException("Organization task result mismatch");return;}
  for(var todo:todos.active(t.getId()))if(todo.getStatus()!=2){
   one(todos.transition(todo.getId(),todo.getStatus(),2,now()));record(t.getId(),todo.getAssignmentId(),TaskRecordType.TODO_COMPLETED,actor,"Organization request resolved");
  }
  assignments.forceComplete(t.getId(),now());
  one(tasks.transition(t.getId(),t.getStatus(),cancelled?3:2,now()));
  record(t.getId(),null,cancelled?TaskRecordType.TASK_CANCELLED:TaskRecordType.TASK_COMPLETED,actor,"Organization request resolved");
 }
 public TaskView view(Long id){return TaskView.builder().task(tasks.find(id)).assignments(assignments.byTask(id)).records(records.byTask(id)).build();}
}
