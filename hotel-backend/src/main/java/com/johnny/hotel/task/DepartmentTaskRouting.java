package com.johnny.hotel.task;

import com.johnny.hotel.organization.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Organization guard precedes root Task, branch Task, Assignment and Todo locks. */
@Component @RequiredArgsConstructor
public class DepartmentTaskRouting {
 private final OrganizationMapper organizations;
 private final DepartmentTaskMapper departments;
 private final HotelTaskMapper tasks;
 private final TaskAssignmentMapper assignments;
 private final TodoMapper todos;
 private final TaskRecordMapper records;
 private final Clock clock;

 public void guard(){require(organizations.guard()!=null,"Organization guard missing");}
 public boolean isBranch(HotelTask task){return Integer.valueOf(1).equals(task.getDepartmentBranch());}
 public boolean isRoot(HotelTask task){return Integer.valueOf(1).equals(task.getDepartmentRoot());}
 public boolean manager(Long department,Long actor){var d=organizations.department(department);return d!=null&&d.getStatus()==1&&Objects.equals(d.getManagerUserId(),actor);}
 public void targets(HotelTask task,List<Long> ids){if(task.getDepartmentId()!=null)require(departments.members(task.getDepartmentId()).containsAll(ids),"Assignees must be active operational members of this department");}

 @Transactional(propagation=Propagation.MANDATORY)
 public void createBranches(HotelTask root,List<Long> departmentIds,Long actor){
  guard();var ids=new TreeSet<>(departmentIds);require(!ids.isEmpty()&&ids.size()==departmentIds.size()&&ids.size()<=100,"Departments must be unique and nonempty");
  one(departments.scope(root.getId(),null,0,1));
  for(Long id:ids){var d=organizations.department(id);require(d!=null&&d.getStatus()==1,"Active department required");
   var branch=HotelTask.builder().taskType(1).executionType(3).status(0).assignmentMode(1).parentTaskId(root.getId()).title(d.getName()).description(root.getDescription()).createdBy(actor).sourceKey("DEPARTMENT_BRANCH:"+root.getId()+":"+id).build();
   one(tasks.insert(branch));one(departments.scope(branch.getId(),id,1,0));branch=tasks.find(branch.getId());record(branch.getId(),actor,TaskRecordType.TASK_CREATED,"Department responsibility snapshot");route(branch,d.getManagerUserId(),actor);
  }
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void managerChanged(Long department,Long manager,Long actor){
  guard();for(var identity:departments.openBranches(department)){
   tasks.lock(identity.getParentTaskId());var branch=tasks.lock(identity.getId());
   if(Integer.valueOf(DepartmentRoutingSource.MANAGEMENT_OVERRIDE.getCode()).equals(branch.getRoutingSource()))continue;
   route(branch,manager,actor);
  }
 }
 private void route(HotelTask branch,Long manager,Long actor){
  var now=LocalDateTime.now(clock);todos.retire(branch.getId(),now);assignments.endCurrent(branch.getId(),now);
  one(tasks.resetAssignment(branch.getId(),0,1));one(departments.source(branch.getId(),DepartmentRoutingSource.AUTO_DEPARTMENT_MANAGER.getCode()));
  record(branch.getId(),actor,TaskRecordType.TASK_REASSIGNED,manager==null?"Department manager vacancy; responsibility unassigned":"Automatic department manager handoff to "+manager);
  if(manager==null||!departments.members(branch.getDepartmentId()).contains(manager))return;
  var assignment=TaskAssignment.builder().taskId(branch.getId()).assigneeUserId(manager).assignedBy(actor).status(0).isCurrent(1).assignmentRound(assignments.maxRound(branch.getId())+1).build();one(assignments.insert(assignment));
  one(todos.insert(Todo.builder().taskId(branch.getId()).assignmentId(assignment.getId()).userId(manager).build()));
  record(branch.getId(),actor,TaskRecordType.TODO_CREATED,"AUTO_DEPARTMENT_MANAGER; explicit acknowledgement required");
 }
 private void record(Long task,Long actor,TaskRecordType type,String detail){one(records.insert(TaskRecord.builder().taskId(task).actorUserId(actor).recordType(type.getCode()).detail(detail).build()));}
}
