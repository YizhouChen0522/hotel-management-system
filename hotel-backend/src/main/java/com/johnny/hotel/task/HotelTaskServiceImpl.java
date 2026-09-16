package com.johnny.hotel.task;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class HotelTaskServiceImpl implements HotelTaskService {
 private final DepartmentTaskRouting departmentRouting;
 private final DepartmentTaskMapper departmentTasks;
 private final HotelTaskMapper tasks;
 private final TaskAssignmentMapper assignments;
 private final TaskRecordMapper records;
 private final TodoMapper todos;
 private final SysUserMapper users;
 private final SysRoleMapper roles;
 private final SysAuditLogMapper audits;
 private final Clock clock;
 private final com.johnny.hotel.pagination.PaginationSupport pagination;
 private final List<TaskLifecycleObserver> lifecycleObservers;
 private record Actor(Long id,boolean manager){}
 private Actor actor(){
  var a=SecurityContextHolder.getContext().getAuthentication();
  if(a==null||!a.isAuthenticated()||!(a.getDetails() instanceof Long id))throw denied();
  return actor(id);
 }
 private Actor actor(Long id){
  var u=users.selectById(id);
  var rs=roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
  if(u==null||!Integer.valueOf(1).equals(u.getStatus())||rs.contains("CUSTOMER")||rs.contains("HR_ADMIN"))throw denied();
  boolean manager=rs.stream().anyMatch(Set.of("MANAGER","OWNER","SUPER_ADMIN")::contains);
  if(!manager&&!rs.contains("STAFF"))throw denied();
  return new Actor(id,manager);
 }
 private AccessDeniedException denied(){return new AccessDeniedException("Hotel task access denied");}
 private void require(boolean ok,String msg){if(!ok)throw new BusinessException(msg);}
 private void conflict(boolean ok){if(!ok)throw new BusinessException(409,"Task state changed or transition is not allowed");}
 private HotelTask found(HotelTask t){if(t==null)throw new BusinessException(404,"Task does not exist");return t;}
 private LocalDateTime now(){return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);}
 private void manager(Actor a){if(!a.manager())throw denied();}
 private boolean open(HotelTask t){return Set.of(0,1,4).contains(t.getStatus());}
 private boolean collective(HotelTask t){return t.getExecutionType()==0||t.getExecutionType()==1;}
 private void record(Long task,Long assignment,TaskRecordType type,Long actor,String detail){
  conflict(records.insert(TaskRecord.builder().taskId(task).assignmentId(assignment).recordType(type.getCode()).actorUserId(actor).detail(normalize(detail)).build())==1);
 }
 private String normalize(String text){if(text==null||text.isBlank())return null;String v=text.trim();require(v.length()<=500,"Note must be at most 500 characters");return v;}
 private void audit(Long actor,Long task,String action){conflict(audits.insert(SysAuditLog.builder().operatorId(actor).action(action).detail("Hotel task "+task).build())==1);}

 // Parent identities are immutable. Every mutation locks the complete ancestor chain root first.
 // This also serializes child creation against parent completion without taking room/financial locks.
 private HotelTask lock(Long id){
  var chain=new ArrayList<Long>();var t=found(tasks.find(id));
  if(t.getDepartmentId()!=null||departmentRouting.isRoot(t))departmentRouting.guard();
  if(t.getTaskType()==2)throw new BusinessException(409,"Use the Organization approval workflow");
  while(true){require(!chain.contains(t.getId())&&chain.size()<32,"Invalid task hierarchy");chain.add(t.getId());if(t.getParentTaskId()==null)break;t=found(tasks.find(t.getParentTaskId()));}
  Collections.reverse(chain);
  for(Long task:chain){t=found(tasks.lock(task));conflict(open(t));}
  return t;
 }
 private HotelTask create(TaskRequests.CreateGeneral r,Actor a,Long parent){
  require(r!=null&&r.getTitle()!=null&&!r.getTitle().isBlank()&&r.getRequestKey()!=null&&!r.getRequestKey().isBlank(),"Title and request key are required");
  String key=r.getRequestKey().trim(),title=r.getTitle().trim(),description=normalize(r.getDescription());
  require(key.length()<=100&&title.length()<=120,"Task fields too long");
  int execution=r.getExecutionType()==null?0:r.getExecutionType().getCode();
  if(!a.manager()&&execution==3)throw denied();
  var old=tasks.byRequest(a.id(),key);
  if(old!=null){require(old.getTitle().equals(title)&&Objects.equals(old.getDescription(),description)&&old.getExecutionType()==execution&&Objects.equals(old.getParentTaskId(),parent),"Request key already represents another task");return old;}
  var t=HotelTask.builder().taskType(1).status(0).assignmentMode(0).executionType(execution).parentTaskId(parent).title(title).description(description).requestKey(key).createdBy(a.id()).build();
  conflict(tasks.insert(t)==1);record(t.getId(),null,TaskRecordType.TASK_CREATED,a.id(),"General task created");audit(a.id(),t.getId(),"CREATE_TASK");return tasks.find(t.getId());
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public HotelTask createGeneral(TaskRequests.CreateGeneral r){
  var a=actor();if(r!=null&&r.getDepartmentIds()!=null){manager(a);require(r.getExecutionType()==TaskExecutionType.GROUP_GROUP,"Departments require GROUP_GROUP root");departmentRouting.guard();
   require(r.getDepartmentIds().stream().allMatch(Objects::nonNull)&&!r.getDepartmentIds().isEmpty()&&new HashSet<>(r.getDepartmentIds()).size()==r.getDepartmentIds().size(),"Departments must be unique and nonempty");
   var old=r.getRequestKey()==null?null:tasks.byRequest(a.id(),r.getRequestKey().trim());var root=create(r,a,null);
   if(old==null)departmentRouting.createBranches(root,r.getDepartmentIds(),a.id());
   else require(new HashSet<>(tasks.children(root.getId()).stream().map(HotelTask::getDepartmentId).toList()).equals(new HashSet<>(r.getDepartmentIds())),"Request department snapshot differs");
   return tasks.find(root.getId());}
  return create(r,a,null);
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public HotelTask subtask(Long id,TaskRequests.CreateGeneral r){
  var a=actor();var parent=lock(id);require(parent.getExecutionType()==3,"Only GROUP_GROUP tasks can have subtasks");
  require(!departmentRouting.isRoot(parent),"Department root branches are fixed at creation");
  if(parent.getDepartmentId()!=null){if(!a.manager()&&!departmentRouting.manager(parent.getDepartmentId(),a.id()))throw denied();require(r!=null&&r.getExecutionType()!=TaskExecutionType.GROUP_GROUP,"Department children use PERSONAL, SHARED or GROUP_INDIVIDUAL");}
  else if(!a.manager()&&assignments.currentForUser(id,a.id())==null)throw denied();
  int depth=1;
  for(var ancestor=parent;ancestor.getParentTaskId()!=null;depth++)ancestor=found(tasks.find(ancestor.getParentTaskId()));
  require(depth<32,"Task hierarchy supports at most 32 levels");
  var old=r==null||r.getRequestKey()==null?null:tasks.byRequest(a.id(),r.getRequestKey().trim());
  var child=create(r,a,id);
  if(old==null){
   if(parent.getDepartmentId()!=null){conflict(departmentTasks.scope(child.getId(),parent.getDepartmentId(),0,0)==1);child=tasks.find(child.getId());
    List<Long> ids=r.getAssigneeUserIds();if(child.getExecutionType()==2&&ids==null)ids=departmentTasks.members(parent.getDepartmentId());
    require(ids!=null&&!ids.isEmpty(),"Department child needs explicit assignees or a GROUP_INDIVIDUAL member snapshot");
    var assign=TaskRequests.Assign.builder().assigneeUserIds(ids).build();assignNew(child,targets(assign),a,TaskRecordType.TASK_ASSIGNED,"Department member snapshot");
   }
   record(id,null,TaskRecordType.SUBTASK_CREATED,a.id(),"Child task "+child.getId());
  }
  return tasks.find(child.getId());
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public HotelTask createTurnover(Long roomId,Long assignmentId,Long actorId){
  actor(actorId);String key="ROOM_TURNOVER_ASSIGNMENT:"+assignmentId;
  var old=tasks.bySourceForUpdate(key);if(old!=null)return old;
  var t=HotelTask.builder().taskType(0).status(0).assignmentMode(0).executionType(0).title("Room turnover for assignment "+assignmentId).description("Room "+roomId+" after assignment "+assignmentId).sourceKey(key).createdBy(actorId).build();
  conflict(tasks.insert(t)==1);record(t.getId(),null,TaskRecordType.TASK_CREATED,actorId,"Turnover task created");return t;
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public HotelTask createMaintenance(Long roomId,String title,String description,String requestKey,Long actorId){
  actor(actorId);var existing=tasks.bySourceForUpdate(requestKey);if(existing!=null)return existing;
  var t=HotelTask.builder().taskType(3).status(0).assignmentMode(0).executionType(0).title(title).description(description).sourceKey(requestKey).createdBy(actorId).build();
  conflict(tasks.insert(t)==1);record(t.getId(),null,TaskRecordType.TASK_CREATED,actorId,"Room maintenance reported for room "+roomId);audit(actorId,t.getId(),"CREATE_MAINTENANCE_TASK");return t;
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public HotelTask createCleaning(Long bookingId,Long roomId,Long assignmentId,String sourceKey,String description,Long actorId){
  var requester=users.selectById(actorId);var requesterRoles=roles.selectRolesByUserId(actorId).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());require(requester!=null&&Integer.valueOf(1).equals(requester.getStatus())&&!requesterRoles.contains("HR_ADMIN")&&requesterRoles.stream().anyMatch(Set.of("CUSTOMER","STAFF","MANAGER","OWNER","SUPER_ADMIN")::contains),"Active customer or operational employee required");var existing=tasks.bySourceForUpdate(sourceKey);if(existing!=null)return existing;
  var t=HotelTask.builder().taskType(4).status(0).assignmentMode(0).executionType(0).title("Stayover cleaning for booking "+bookingId).description(description).sourceKey(sourceKey).createdBy(actorId).build();
  conflict(tasks.insert(t)==1);record(t.getId(),null,TaskRecordType.TASK_CREATED,actorId,"Room cleaning requested for assignment "+assignmentId);record(t.getId(),null,TaskRecordType.CLEANING_REQUESTED,actorId,"Room "+roomId);audit(actorId,t.getId(),"REQUEST_ROOM_CLEANING");return t;
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public HotelTask createGuestService(Long bookingId,String sourceKey,String title,String description,Long actorId){
  var requester=users.selectById(actorId);var requesterRoles=roles.selectRolesByUserId(actorId).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());require(requester!=null&&Integer.valueOf(1).equals(requester.getStatus())&&!requesterRoles.contains("HR_ADMIN")&&requesterRoles.stream().anyMatch(Set.of("CUSTOMER","STAFF","MANAGER","OWNER","SUPER_ADMIN")::contains),"Active customer or operational employee required");var existing=tasks.bySourceForUpdate(sourceKey);if(existing!=null)return existing;
  var t=HotelTask.builder().taskType(6).status(0).assignmentMode(0).executionType(0).title(title).description(description).sourceKey(sourceKey).createdBy(actorId).build();conflict(tasks.insert(t)==1);record(t.getId(),null,TaskRecordType.TASK_CREATED,actorId,"Guest service requested for booking "+bookingId);audit(actorId,t.getId(),"CREATE_GUEST_SERVICE_TASK");return t;
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public HotelTask createRepair(Long roomId,String sourceKey,String description,Long actorId){actor(actorId);var existing=tasks.bySourceForUpdate(sourceKey);if(existing!=null)return existing;var t=HotelTask.builder().taskType(5).status(0).assignmentMode(0).executionType(0).title("Structural repair for room "+roomId).description(description).sourceKey(sourceKey).createdBy(actorId).build();conflict(tasks.insert(t)==1);record(t.getId(),null,TaskRecordType.TASK_CREATED,actorId,"Structural repair required for room "+roomId);audit(actorId,t.getId(),"CREATE_ROOM_REPAIR_TASK");return t;}
 @Override public List<HotelTask> list(Integer status,Integer type,Integer page,Integer size){
  actor();if(status!=null)require(status>=0&&status<=4,"Invalid task status");if(type!=null)require(Set.of(0,1,4,5,6).contains(type),"Invalid task type");
  int[] p=pagination(page,size);return tasks.page(status,type,p[0],p[1]);
 }
 private int[] pagination(Integer page,Integer size){int p=page==null?1:page,s=size==null?50:size;require(p>0&&s>0&&s<=100&&((long)p-1)*s<=Integer.MAX_VALUE,"Invalid pagination");return new int[]{(p-1)*s,s};}
 @Override public TaskView get(Long id){actor();var t=found(tasks.find(id));if(t.getTaskType()==2)throw denied();return view(t);}
 @Override public com.johnny.hotel.pagination.PageResult<TaskRecord> records(Long id,Integer type,Long actorId,Integer page,Integer size){actor();var t=found(tasks.find(id));if(t.getTaskType()==2)throw denied();var w=pagination.window(page,size,true);int limit=pagination.limit(w);var rows=limit==0?List.<TaskRecord>of():records.page(id,type,actorId,w.offset(),limit);return pagination.result(w,rows,records.count(id,type,actorId));}
 @Override public List<TaskAssignment> myTodo(){return assignments.todo(actor().id());}
 private TaskView view(HotelTask t){return TaskView.builder().task(t).assignments(assignments.byTask(t.getId())).records(records.byTask(t.getId())).build();}
 @Override public List<Todo> todos(Integer page,Integer size){var a=actor();int[] p=pagination(page,size);return todos.mine(a.id(),p[0],p[1]);}
 private Todo owned(Long id,Actor a){var t=todos.owned(id,a.id());if(t==null)throw new BusinessException(404,"Todo not found");return t;}
 @Override public Todo todo(Long id){return owned(id,actor());}

 private void add(HotelTask t,Long user,Actor a,int round,TaskRecordType event,String note){
  conflict(assignments.currentForUser(t.getId(),user)==null);
  var x=TaskAssignment.builder().taskId(t.getId()).assigneeUserId(user).assignedBy(a.id()).status(0).assignmentRound(round).isCurrent(1).build();
  conflict(assignments.insert(x)==1);
  var todo=Todo.builder().taskId(t.getId()).assignmentId(x.getId()).userId(user).build();
  conflict(todos.insert(todo)==1);
  record(t.getId(),x.getId(),event,a.id(),note);
  record(t.getId(),x.getId(),TaskRecordType.TODO_CREATED,a.id(),"Todo "+todo.getId());
 }
 private void state(HotelTask t,int next,Actor a){
  int old=t.getStatus();if(old==next)return;
  conflict(tasks.transition(t.getId(),old,next,now())==1);t.setStatus(next);
  if(next==4)record(t.getId(),null,TaskRecordType.TASK_BLOCKED,a.id(),null);
  if(old==4&&next==1)record(t.getId(),null,TaskRecordType.TASK_RESUMED,a.id(),null);
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView claim(Long id){
  var a=actor();var t=lock(id);if(t.getTaskType()==6)throw new BusinessException(409,"Use the Guest Service claim endpoint");
  require(t.getDepartmentId()==null&&!departmentRouting.isRoot(t),"Department tasks must be routed or assigned");
  boolean initial=t.getStatus()==0&&t.getAssignmentMode()==0&&assignments.currentCount(id)==0;
  boolean rescue=t.getStatus()==4&&collective(t);
  boolean sharedPartner=t.getStatus()==1&&t.getAssignmentMode()==0&&t.getExecutionType()==1&&assignments.currentCount(id)==1;
  conflict((initial||rescue||sharedPartner)&&t.getExecutionType()!=3);
  // A blocked PERSONAL task deliberately retains its previous responsible employee.
  add(t,a.id(),a,assignments.maxRound(id)+1,TaskRecordType.TASK_CLAIMED,null);
  state(t,1,a);audit(a.id(),id,"CLAIM_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView accept(Long id){
  var a=actor();var t=lock(id);if(t.getTaskType()==6)throw new BusinessException(409,"Use the Guest Service accept endpoint");var todo=todos.current(id,a.id());if(todo==null)throw denied();
  mutate(t,todo,a,"ACKNOWLEDGE",null);lifecycleObservers.forEach(observer->observer.acknowledged(t,todo,a.id()));return view(tasks.find(id));
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView complete(Long id,String note){
  var a=actor();var t=lock(id);if(Set.of(0,4).contains(t.getTaskType()))throw new BusinessException(409,"Use the Cleaning completion endpoint");if(t.getTaskType()==3)throw new BusinessException(409,"Use the WorkOrder completion endpoint");if(t.getTaskType()==5)throw new BusinessException(409,"Use the RepairOrder completion endpoint");if(t.getTaskType()==6)throw new BusinessException(409,"Use the Guest Service completion endpoint");var todo=todos.current(id,a.id());if(todo==null)throw denied();
  mutate(t,todo,a,"COMPLETE",note);return view(tasks.find(id));
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public Todo updateTodo(Long id,String action,String note){
  var a=actor();var identity=owned(id,a);var task=lock(identity.getTaskId());
  if(Set.of(0,4).contains(task.getTaskType())&&"COMPLETE".equals(action))throw new BusinessException(409,"Use the Cleaning completion endpoint");if(task.getTaskType()==3&&"COMPLETE".equals(action))throw new BusinessException(409,"Use the WorkOrder completion endpoint");if(task.getTaskType()==5&&"COMPLETE".equals(action))throw new BusinessException(409,"Use the RepairOrder completion endpoint");if(task.getTaskType()==6&&"COMPLETE".equals(action))throw new BusinessException(409,"Use the Guest Service completion endpoint");
  var todo=owned(id,a);mutate(task,todo,a,action,note);if("ACKNOWLEDGE".equals(action))lifecycleObservers.forEach(observer->observer.acknowledged(task,todo,a.id()));return owned(id,a);
 }
 private void mutate(HotelTask task,Todo todo,Actor a,String action,String note){
  conflict(todo.getIsActive()==1);normalize(note);int before=todo.getStatus();int next;TaskRecordType event;
  switch(action){
   case "ACKNOWLEDGE" -> {conflict(before==0);next=1;event=TaskRecordType.TODO_ACKNOWLEDGED;}
   case "BLOCK" -> {conflict(before==1);next=3;event=TaskRecordType.TODO_BLOCKED;}
   case "RESUME" -> {conflict(before==3||(before==1&&collective(task)&&task.getStatus()==4));next=1;event=TaskRecordType.TODO_RESUMED;}
   case "COMPLETE" -> {if(departmentRouting.isBranch(task))require(childrenDone(task),"Complete required department subtasks before branch confirmation");conflict(before==1);require(task.getExecutionType()!=1||assignments.currentCount(task.getId())>=2,"SHARED task requires at least two responsible employees");next=2;event=TaskRecordType.TODO_COMPLETED;}
   default -> throw new BusinessException("Unknown Todo action");
  }
  conflict(todos.transition(todo.getId(),before,next,now())==1);
  if(before==0){conflict(assignments.accept(todo.getAssignmentId(),now())==1);record(task.getId(),todo.getAssignmentId(),TaskRecordType.TASK_ACCEPTED,a.id(),note);}
  if(next==2){conflict(assignments.complete(todo.getAssignmentId(),now())==1);record(task.getId(),todo.getAssignmentId(),TaskRecordType.TASK_COMPLETED,a.id(),note);}
  record(task.getId(),todo.getAssignmentId(),event,a.id(),note);
  if(collective(task)){
   if(next==3)state(task,4,a);
   else if(next==2){finishTodos(task,a,note);state(task,2,a);}
   else if(action.equals("RESUME"))state(task,1,a);
  }
  sync(task,a);
  audit(a.id(),task.getId(),"TODO_"+action);
 }
 private void finishTodos(HotelTask task,Actor a,String note){
  for(var row:todos.active(task.getId()))if(row.getStatus()!=2){
   conflict(todos.transition(row.getId(),row.getStatus(),2,now())==1);
   record(task.getId(),row.getAssignmentId(),TaskRecordType.TODO_COMPLETED,a.id(),note);
  }
  assignments.forceComplete(task.getId(),now());
 }
 private boolean childrenDone(HotelTask task){return tasks.children(task.getId()).stream().allMatch(c->c.getStatus()==2);}
 private void sync(HotelTask task,Actor a){
  if(open(task)&&departmentRouting.isRoot(task)){
   var branches=tasks.children(task.getId());if(!branches.isEmpty()&&branches.stream().allMatch(c->c.getStatus()==2)){state(task,2,a);record(task.getId(),null,TaskRecordType.TASK_COMPLETED,a.id(),"All department branches confirmed");}
  }else if(open(task)&&!collective(task)){
   var rows=todos.active(task.getId());
   if(!rows.isEmpty()&&rows.stream().allMatch(x->x.getStatus()==2)&&childrenDone(task)){state(task,2,a);record(task.getId(),null,TaskRecordType.TASK_COMPLETED,a.id(),"All responsibilities and subtasks completed");}
   else if(!rows.isEmpty())state(task,1,a);
  }
  if(task.getParentTaskId()!=null)sync(found(tasks.lock(task.getParentTaskId())),a);
 }
 private List<Long> targets(TaskRequests.Assign r){
  require(r!=null&&r.getAssigneeUserIds()!=null,"Assignees required");
  var ids=new LinkedHashSet<>(r.getAssigneeUserIds());require(!ids.isEmpty()&&ids.size()==r.getAssigneeUserIds().size()&&ids.size()<=100,"Assignees must be unique, at most 100");
  for(Long id:ids){require(id!=null,"Assignee required");actor(id);}return new ArrayList<>(ids);
 }
 private void assignmentManager(HotelTask t,Actor a){if(a.manager())return;if(t.getDepartmentId()==null||departmentRouting.isBranch(t)||!departmentRouting.manager(t.getDepartmentId(),a.id()))throw denied();}
 private void validateCount(HotelTask t,int count){
  if(t.getExecutionType()==0)require(count==1,"PERSONAL assignment requires one employee; additional claims require BLOCKED");
  if(t.getExecutionType()==1)require(count>=2,"SHARED assignment requires at least two employees");
 }
 private void assignNew(HotelTask t,List<Long> ids,Actor a,TaskRecordType event,String note){
  if(departmentRouting.isBranch(t))record(t.getId(),null,TaskRecordType.TASK_REASSIGNED,a.id(),"MANAGEMENT_OVERRIDE");
  require(!departmentRouting.isRoot(t),"Assign department branches, not the department root");
  departmentRouting.targets(t,ids);
  if(departmentRouting.isBranch(t)){manager(a);require(ids.size()==1&&assignments.currentCount(t.getId())==0,"Department branch has one responsible employee");conflict(departmentTasks.source(t.getId(),DepartmentRoutingSource.MANAGEMENT_OVERRIDE.getCode())==1);}
  int count=assignments.currentCount(t.getId())+ids.size();
  if(t.getTaskType()==3&&t.getExecutionType()==0&&count>=2){conflict(tasks.promoteMaintenanceShared(t.getId())==1);t.setExecutionType(1);}
  validateCount(t,count);
  int round=assignments.maxRound(t.getId())+1;
  for(Long id:ids)add(t,id,a,round,event,note);
  if(t.getStatus()==4)record(t.getId(),null,TaskRecordType.TASK_RESUMED,a.id(),note);
  conflict(tasks.resetAssignment(t.getId(),departmentRouting.isBranch(t)?0:1,count==1?1:2)==1);
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView assign(Long id,TaskRequests.Assign r){
  var a=actor();var t=lock(id);assignmentManager(t,a);conflict(assignments.currentCount(id)==0);
  assignNew(t,targets(r),a,TaskRecordType.TASK_ASSIGNED,r.getNote());audit(a.id(),id,"ASSIGN_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView addAssignees(Long id,TaskRequests.Assign r){
  var a=actor();var t=lock(id);assignmentManager(t,a);
  assignNew(t,targets(r),a,TaskRecordType.ASSIGNEE_ADDED,r.getNote());audit(a.id(),id,"ADD_ASSIGNEES");return view(tasks.find(id));
 }
 private void retire(HotelTask t,Actor a,String note){
  for(var row:todos.active(t.getId()))record(t.getId(),row.getAssignmentId(),TaskRecordType.TODO_RETIRED,a.id(),note);
  todos.retire(t.getId(),now());assignments.endCurrent(t.getId(),now());
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView reassign(Long id,TaskRequests.Assign r){
  var a=actor();var t=lock(id);assignmentManager(t,a);var ids=targets(r);retire(t,a,r.getNote());
  assignNew(t,ids,a,TaskRecordType.TASK_REASSIGNED,r.getNote());audit(a.id(),id,"REASSIGN_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView cancel(Long id,String note){
  var a=actor();manager(a);var t=lock(id);
  if(Set.of(0,4).contains(t.getTaskType()))throw new BusinessException(409,"Use the Cleaning cancellation endpoint");if(t.getTaskType()==3)throw new BusinessException(409,"Use the WorkOrder cancellation endpoint");if(t.getTaskType()==5)throw new BusinessException(409,"Use the RepairOrder cancellation endpoint");if(t.getTaskType()==6)throw new BusinessException(409,"Use the Guest Service cancellation endpoint");
  require(tasks.children(id).stream().noneMatch(this::open),"Resolve active subtasks before cancellation");
  retire(t,a,note);state(t,3,a);record(id,null,TaskRecordType.TASK_CANCELLED,a.id(),note);audit(a.id(),id,"CANCEL_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(isolation=Isolation.READ_COMMITTED) public TaskView forceComplete(Long id,String note){
  var a=actor();manager(a);var t=lock(id);require(childrenDone(t),"Complete all subtasks before completing the parent");
  if(Set.of(0,4).contains(t.getTaskType()))throw new BusinessException(409,"Use the Cleaning completion endpoint");if(t.getTaskType()==3)throw new BusinessException(409,"Use the WorkOrder completion endpoint");if(t.getTaskType()==5)throw new BusinessException(409,"Use the RepairOrder completion endpoint");if(t.getTaskType()==6)throw new BusinessException(409,"Use the Guest Service completion endpoint");
  finishTodos(t,a,note);conflict(tasks.forceComplete(id,now())==1);t.setStatus(2);
  record(id,null,TaskRecordType.TASK_FORCE_COMPLETED,a.id(),note);sync(t,a);audit(a.id(),id,"FORCE_COMPLETE_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public TaskView completeMaintenance(Long id,String note,boolean force){
  var a=actor();var t=lock(id);require(t.getTaskType()==3,"Maintenance task required");
  if(force){manager(a);finishTodos(t,a,note);conflict(tasks.forceComplete(id,now())==1);t.setStatus(2);record(id,null,TaskRecordType.TASK_FORCE_COMPLETED,a.id(),note);}
  else {var todo=todos.current(id,a.id());if(todo==null)throw denied();mutate(t,todo,a,"COMPLETE",note);}
  audit(a.id(),id,"COMPLETE_MAINTENANCE_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public TaskView cancelMaintenance(Long id,String note){
  var a=actor();manager(a);var t=lock(id);require(t.getTaskType()==3,"Maintenance task required");retire(t,a,note);state(t,3,a);record(id,null,TaskRecordType.TASK_CANCELLED,a.id(),note);audit(a.id(),id,"CANCEL_MAINTENANCE_TASK");return view(tasks.find(id));
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public TaskView completeCleaning(Long id,String note,boolean force){var a=actor();var t=lock(id);require(Set.of(0,4).contains(t.getTaskType()),"Cleaning task required");if(force){manager(a);finishTodos(t,a,note);conflict(tasks.forceComplete(id,now())==1);t.setStatus(2);record(id,null,TaskRecordType.TASK_FORCE_COMPLETED,a.id(),note);}else{var todo=todos.current(id,a.id());if(todo==null)throw denied();mutate(t,todo,a,"COMPLETE",note);}record(id,null,TaskRecordType.CLEANING_COMPLETED,a.id(),note);audit(a.id(),id,"COMPLETE_CLEANING_TASK");return view(tasks.find(id));}
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public TaskView cancelCleaning(Long id,String note){var a=actor();manager(a);var t=lock(id);require(Set.of(0,4).contains(t.getTaskType()),"Cleaning task required");retire(t,a,note);state(t,3,a);record(id,null,TaskRecordType.CLEANING_CANCELLED,a.id(),note);record(id,null,TaskRecordType.TASK_CANCELLED,a.id(),note);audit(a.id(),id,"CANCEL_CLEANING_TASK");return view(tasks.find(id));}
 @Override @Transactional(propagation=Propagation.MANDATORY) public TaskView claimGuestService(Long id){var a=actor();var t=lock(id);require(t.getTaskType()==6,"Guest service task required");conflict(t.getStatus()==0&&t.getAssignmentMode()==0&&assignments.currentCount(id)==0);add(t,a.id(),a,assignments.maxRound(id)+1,TaskRecordType.TASK_CLAIMED,null);state(t,1,a);audit(a.id(),id,"CLAIM_GUEST_SERVICE");return view(tasks.find(id));}
 @Override @Transactional(propagation=Propagation.MANDATORY) public TaskView completeGuestService(Long id,String note,boolean force){var a=actor();var t=lock(id);require(t.getTaskType()==6,"Guest service task required");if(force){manager(a);finishTodos(t,a,note);conflict(tasks.forceComplete(id,now())==1);t.setStatus(2);record(id,null,TaskRecordType.TASK_FORCE_COMPLETED,a.id(),note);}else{var todo=todos.current(id,a.id());if(todo==null)throw denied();mutate(t,todo,a,"COMPLETE",note);}audit(a.id(),id,"COMPLETE_GUEST_SERVICE");return view(tasks.find(id));}
 @Override @Transactional(propagation=Propagation.MANDATORY) public TaskView cancelGuestService(Long id,String note){var a=actor();var t=lock(id);require(t.getTaskType()==6,"Guest service task required");retire(t,a,note);state(t,3,a);record(id,null,TaskRecordType.TASK_CANCELLED,a.id(),note);audit(a.id(),id,"CANCEL_GUEST_SERVICE");return view(tasks.find(id));}
 @Override @Transactional(propagation=Propagation.MANDATORY) public TaskView completeRepair(Long id,String note,boolean force){var a=actor();var t=lock(id);require(t.getTaskType()==5,"Repair task required");if(force){manager(a);finishTodos(t,a,note);conflict(tasks.forceComplete(id,now())==1);t.setStatus(2);record(id,null,TaskRecordType.TASK_FORCE_COMPLETED,a.id(),note);}else{var todo=todos.current(id,a.id());if(todo==null)throw denied();mutate(t,todo,a,"COMPLETE",note);}audit(a.id(),id,"COMPLETE_REPAIR");return view(tasks.find(id));}
 @Override @Transactional(propagation=Propagation.MANDATORY) public TaskView cancelRepair(Long id,String note){var a=actor();manager(a);var t=lock(id);require(t.getTaskType()==5,"Repair task required");retire(t,a,note);state(t,3,a);record(id,null,TaskRecordType.TASK_CANCELLED,a.id(),note);audit(a.id(),id,"CANCEL_REPAIR");return view(tasks.find(id));}
}
