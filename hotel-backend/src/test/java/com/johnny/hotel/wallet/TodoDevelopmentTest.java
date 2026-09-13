package com.johnny.hotel.wallet;
import com.johnny.hotel.task.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class TodoDevelopmentTest extends FinancialDevelopmentFixture {
 @Autowired HotelTaskService tasks; @Autowired TodoService todoService; @Autowired org.flywaydb.core.Flyway flyway;
 TaskRequests.CreateGeneral req(TaskExecutionType type,String key){return TaskRequests.CreateGeneral.builder().title("Todo test").requestKey(key).executionType(type).build();}
 long general(TaskExecutionType type){as("MANAGER");return tasks.createGeneral(req(type,"todo_001")).getId();}
 TaskRequests.Assign target(String... names){return TaskRequests.Assign.builder().assigneeUserIds(Arrays.stream(names).map(this::uid).toList()).build();}
 long my(long task){return todoService.mine(1,100).stream().filter(t->t.getTaskId()==task).findFirst().orElseThrow().getId();}
 int taskStatus(long task){return tasks.get(task).getTask().getStatus();}
 void assign(long task,String... names){as("MANAGER");tasks.assign(task,target(names));}
 void ack(String role,long task){as(role);todoService.acknowledge(my(task),null);}
 void done(String role,long task){as(role);todoService.complete(my(task),"Done");}
 int count(long task,String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE task_id=?",Integer.class,task);}
 @Test void migrationAndPresetsHaveOneTodoPerAssignment(){
  flyway.validate();assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='16'",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM task_assignment a LEFT JOIN todo d ON d.assignment_id=a.id WHERE d.id IS NULL OR d.task_id<>a.task_id OR d.user_id<>a.assignee_user_id",Integer.class));
  assertEquals(4,TaskStatus.BLOCKED.getCode());assertEquals(3,TodoStatus.BLOCKED.getCode());
 }
 @Test void staffSelfClaimCreatesPendingTodoAndRequiresAcknowledgment(){
  as("STAFF");var task=tasks.createGeneral(req(TaskExecutionType.PERSONAL,"self_001"));assertEquals(0,task.getStatus());assertEquals(0,task.getAssignmentMode());
  tasks.claim(task.getId());long id=my(task.getId());assertEquals(0,todoService.get(id).getStatus());assertEquals(1,taskStatus(task.getId()));
  assertThrows(BusinessException.class,()->todoService.complete(id,null));todoService.acknowledge(id,null);assertEquals(1,todoService.get(id).getStatus());assertNotNull(todoService.get(id).getAcknowledgedTime());
  todoService.complete(id,null);assertEquals(2,taskStatus(task.getId()));assertTrue(todoService.mine(1,100).isEmpty());
 }
 @ParameterizedTest @ValueSource(strings={"MANAGER","OWNER","SUPER_ADMIN"})
 void managementAssignCreatesPendingPersonalTodoImmediatelyInProgress(String manager){
  long task=general(TaskExecutionType.PERSONAL);as(manager);tasks.assign(task,target("STAFF"));assertEquals(1,taskStatus(task));
  as("STAFF");assertEquals(0,todoService.get(my(task)).getStatus());
 }
 @Test void personalBlockedRescueKeepsOriginalTodoAndCompletesSingleOutcome(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");ack("STAFF",task);long original=my(task);
  todoService.block(original,"Equipment unavailable");assertEquals(4,taskStatus(task));as("OWNER");tasks.claim(task);
  assertEquals(1,taskStatus(task));assertEquals(2,count(task,"todo"));assertEquals(2,count(task,"task_assignment"));
  as("STAFF");assertEquals(3,todoService.get(original).getStatus());ack("OWNER",task);done("OWNER",task);
  as("STAFF");assertEquals(2,todoService.get(original).getStatus());assertEquals(2,taskStatus(task));
 }
 @Test void personalCannotGainExtraAssigneesWithoutBlockedClaim(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");as("MANAGER");
  assertThrows(BusinessException.class,()->tasks.addAssignees(task,target("OWNER")));
  as("OWNER");assertThrows(BusinessException.class,()->tasks.claim(task));
 }
 @Test void groupIndividualBlockedDoesNotBlockTaskOrTransferResponsibility(){
  long task=general(TaskExecutionType.GROUP_INDIVIDUAL);assign(task,"STAFF","OWNER");ack("STAFF",task);long blocked=my(task);todoService.block(blocked,"Need document");
  assertEquals(1,taskStatus(task));as("SUPER_ADMIN");assertThrows(BusinessException.class,()->tasks.claim(task));assertThrows(BusinessException.class,()->todoService.complete(blocked,null));
  ack("OWNER",task);done("OWNER",task);assertEquals(1,taskStatus(task));as("STAFF");todoService.resume(blocked,null);todoService.complete(blocked,null);assertEquals(2,taskStatus(task));
 }
 @Test void sharedBlockOtherResponsibleResumeAndCompleteSynchronizesTodos(){
  long task=general(TaskExecutionType.SHARED);assign(task,"STAFF","OWNER");ack("STAFF",task);long original=my(task);ack("OWNER",task);
  as("STAFF");todoService.block(original,null);as("OWNER");assertEquals(4,taskStatus(task));todoService.resume(my(task),null);assertEquals(1,taskStatus(task));
  done("OWNER",task);assertEquals(2,taskStatus(task));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND status<>2",Integer.class,task));
 }
 @Test void sharedBlockedCanAddClaimantButExistingOwnerCannotDuplicate(){
  long task=general(TaskExecutionType.SHARED);assign(task,"STAFF","OWNER");ack("STAFF",task);todoService.block(my(task),null);
  assertThrows(BusinessException.class,()->tasks.claim(task));as("SUPER_ADMIN");tasks.claim(task);assertEquals(3,count(task,"todo"));assertEquals(1,taskStatus(task));
 }
 @Test void sharedAssignNeedsMultiplePeople(){long task=general(TaskExecutionType.SHARED);assertThrows(BusinessException.class,()->assign(task,"STAFF"));assertEquals(0,count(task,"todo"));}
 @Test void claimableSharedWaitsForSecondPartnerBeforeCompletion(){
  long task=general(TaskExecutionType.SHARED);as("STAFF");tasks.claim(task);ack("STAFF",task);
  assertThrows(BusinessException.class,()->todoService.complete(my(task),null));
  as("OWNER");tasks.claim(task);assertEquals(2,count(task,"todo"));as("SUPER_ADMIN");assertThrows(BusinessException.class,()->tasks.claim(task));
  done("STAFF",task);assertEquals(2,taskStatus(task));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND status=2 AND acknowledged_time IS NULL",Integer.class,task));
 }
 @Test void reassignSameEmployeePreservesOldTodoAndUniqueActiveTodo(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");as("MANAGER");tasks.reassign(task,target("STAFF"));
  assertEquals(2,count(task,"todo"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND is_active=1",Integer.class,task));
 }
 @Test void concurrentSharedCompletionAndBlockStayConsistent(){
  long task=general(TaskExecutionType.SHARED);assign(task,"STAFF","OWNER");ack("STAFF",task);long staff=my(task);ack("OWNER",task);long owner=my(task);
  assertInstanceOf(BusinessException.class,serialized("TodoMapper.transition",()->{as("STAFF");todoService.complete(staff,null);},()->{as("OWNER");todoService.block(owner,null);}));
  assertEquals(2,taskStatus(task));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND status<>2",Integer.class,task));
 }
 @Test void concurrentReassignmentRejectsRetiredTodoMutation(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");ack("STAFF",task);long previous=my(task);
  assertInstanceOf(BusinessException.class,serialized("TodoMapper.retire",()->{as("MANAGER");tasks.reassign(task,target("OWNER"));},()->{as("STAFF");todoService.complete(previous,null);}));
  assertEquals(1,taskStatus(task));assertEquals(2,count(task,"todo"));
 }
 @Test void nestedParentCompletionPropagatesWithoutSkippingChildren(){
  long root=general(TaskExecutionType.GROUP_GROUP);assign(root,"OWNER");
  var child=tasks.subtask(root,req(TaskExecutionType.GROUP_GROUP,"nested_group"));assign(child.getId(),"OWNER");
  var leaf=tasks.subtask(child.getId(),req(TaskExecutionType.PERSONAL,"nested_leaf"));assign(leaf.getId(),"STAFF");
  ack("OWNER",root);done("OWNER",root);ack("OWNER",child.getId());done("OWNER",child.getId());assertEquals(1,taskStatus(root));
  ack("STAFF",leaf.getId());done("STAFF",leaf.getId());assertEquals(2,taskStatus(child.getId()));assertEquals(2,taskStatus(root));
 }
 @Test void depthLimitRejectsCreationBeforePersistingAnUnusableChild(){
  long parent=general(TaskExecutionType.GROUP_GROUP);
  for(int level=2;level<=32;level++)parent=tasks.subtask(parent,req(TaskExecutionType.GROUP_GROUP,"level_"+level)).getId();
  long leaf=parent;
  assertThrows(BusinessException.class,()->tasks.subtask(leaf,req(TaskExecutionType.GROUP_GROUP,"too_deep")));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_task WHERE parent_task_id=?",Integer.class,leaf));
  tasks.forceComplete(leaf,null);assertEquals(2,taskStatus(leaf));
 }
 @Test void groupGroupRepresentativesChildrenAndAutomaticParentCompletion(){
  long parent=general(TaskExecutionType.GROUP_GROUP);assign(parent,"STAFF","OWNER");
  as("STAFF");var child=tasks.subtask(parent,req(TaskExecutionType.GROUP_INDIVIDUAL,"child_001"));assertEquals(parent,child.getParentTaskId());
  assertEquals(child.getId(),tasks.subtask(parent,req(TaskExecutionType.GROUP_INDIVIDUAL,"child_001")).getId());
  as("MANAGER");assertThrows(BusinessException.class,()->tasks.forceComplete(parent,null));
  ack("STAFF",parent);done("STAFF",parent);ack("OWNER",parent);done("OWNER",parent);assertEquals(1,taskStatus(parent));
  assign(child.getId(),"STAFF");ack("STAFF",child.getId());done("STAFF",child.getId());assertEquals(2,taskStatus(parent));
 }
 @Test void groupGroupCannotBeClaimedAndUnrelatedStaffCannotCreateChildren(){
  long parent=general(TaskExecutionType.GROUP_GROUP);as("STAFF");assertThrows(BusinessException.class,()->tasks.claim(parent));
  assertThrows(AccessDeniedException.class,()->tasks.subtask(parent,req(TaskExecutionType.PERSONAL,"child_001")));
  assertThrows(AccessDeniedException.class,()->tasks.createGeneral(req(TaskExecutionType.GROUP_GROUP,"staff_group")));
 }
 @Test void reassignRetiresOldTodoWithoutDeletingHistory(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");ack("STAFF",task);long old=my(task);
  as("MANAGER");tasks.reassign(task,target("OWNER"));as("STAFF");assertEquals(0,todoService.get(old).getIsActive());assertTrue(todoService.mine(1,100).isEmpty());assertThrows(BusinessException.class,()->todoService.block(old,null));
  assertEquals(2,count(task,"todo"));assertEquals(2,count(task,"task_assignment"));as("OWNER");assertEquals(0,todoService.get(my(task)).getStatus());
 }
 @Test void addAssigneesCreatesTodoAndRejectsDuplicateAtomically(){
  long task=general(TaskExecutionType.GROUP_INDIVIDUAL);assign(task,"STAFF");as("MANAGER");tasks.addAssignees(task,target("OWNER"));assertEquals(2,count(task,"todo"));
  assertThrows(BusinessException.class,()->tasks.addAssignees(task,target("SUPER_ADMIN","STAFF")));assertEquals(2,count(task,"todo"));
 }
 @Test void cancelRetiresAndForceCompletesAllTodos(){
  long task=general(TaskExecutionType.SHARED);assign(task,"STAFF","OWNER");as("MANAGER");tasks.cancel(task,"Cancelled");assertEquals(3,taskStatus(task));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND is_active=1",Integer.class,task));
  long other=tasks.createGeneral(req(TaskExecutionType.SHARED,"other_001")).getId();assign(other,"STAFF","OWNER");tasks.forceComplete(other,null);assertEquals(2,taskStatus(other));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND status<>2",Integer.class,other));
 }
 @Test void todoReadAndWriteAreOwnerScopedIncludingManager()throws Exception{
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");as("STAFF");long id=my(task);
  for(String role:List.of("OWNER","MANAGER")){as(role);assertThrows(BusinessException.class,()->todoService.get(id));mvc.perform(get("/api/todos/my/"+id).with(authentication(auth(role)))).andExpect(status().isNotFound());for(String action:List.of("acknowledge","block","resume","complete"))mvc.perform(post("/api/todos/"+id+"/"+action).with(authentication(auth(role)))).andExpect(status().isNotFound());}
 }
 @ParameterizedTest @ValueSource(strings={"CUSTOMER","HR_ADMIN"})
 void forbiddenRolesAndAnonymous(String role)throws Exception{
  mvc.perform(get("/api/todos/my").with(authentication(auth(role)))).andExpect(status().isForbidden());
  mvc.perform(post("/api/todos/123/block").with(authentication(auth(role)))).andExpect(status().isForbidden());
  mvc.perform(get("/api/todos/my").with(anonymous())).andExpect(status().isUnauthorized());
 }
 @Test void staffCannotUseWorkAssign()throws Exception{
  long task=general(TaskExecutionType.PERSONAL);
  for(String action:List.of("assign","reassign","assignees","cancel","force-complete"))mvc.perform(post("/api/admin/tasks/"+task+"/"+action).with(authentication(auth("STAFF"))).contentType("application/json").content("{\"assigneeUserIds\":["+uid("OWNER")+"]}")).andExpect(status().isForbidden());
 }
 @Test void httpTodoLifecycleAndJwtOwner()throws Exception{
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");as("STAFF");long id=my(task);
  mvc.perform(get("/api/todos/my").with(authentication(auth("STAFF")))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].userId").value(uid("STAFF")));
  for(String action:List.of("acknowledge","block","resume","complete"))mvc.perform(post("/api/todos/"+id+"/"+action).with(authentication(auth("STAFF")))).andExpect(status().isOk());
  as("STAFF");assertEquals(2,taskStatus(task));
  assertTrue(jdbc.queryForList("SELECT record_type FROM task_record WHERE task_id=?",Integer.class,task).containsAll(List.of(8,9,10,11,12,13,14)));
 }
 @ParameterizedTest @ValueSource(strings={"TodoMapper.insert","TaskRecordMapper.insert","SysAuditLogMapper.insert"})
 void assignmentAndTodoCreationRollback(String statement){
  long task=general(TaskExecutionType.PERSONAL);as("MANAGER");gate.arm(Thread.currentThread().getName(),statement,true);
  assertThrows(Exception.class,()->tasks.assign(task,target("STAFF")));assertEquals(0,count(task,"todo"));assertEquals(0,count(task,"task_assignment"));assertEquals(0,taskStatus(task));
 }
 @Test void todoMutationFailureRollsBackAllStates(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");ack("STAFF",task);long id=my(task);int before=count(task,"task_record");
  gate.arm(Thread.currentThread().getName(),"SysAuditLogMapper.insert",true);assertThrows(Exception.class,()->todoService.complete(id,null));
  assertEquals(1,todoService.get(id).getStatus());assertEquals(1,taskStatus(task));assertEquals(before,count(task,"task_record"));
 }
 @Test void concurrentClaimCreatesOneTodo(){
  long task=general(TaskExecutionType.PERSONAL);
  assertInstanceOf(BusinessException.class,serialized("HotelTaskMapper.transition",()->{as("STAFF");tasks.claim(task);},()->{as("OWNER");tasks.claim(task);}));
  assertEquals(1,count(task,"todo"));assertEquals(1,count(task,"task_assignment"));
 }
 @Test void concurrentBlockedClaimOnlyOneRescuer(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");ack("STAFF",task);todoService.block(my(task),null);
  assertInstanceOf(BusinessException.class,serialized("HotelTaskMapper.transition",()->{as("OWNER");tasks.claim(task);},()->{as("SUPER_ADMIN");tasks.claim(task);}));
  assertEquals(2,count(task,"todo"));
 }
 @Test void concurrentAssignCannotDuplicate(){
  long task=general(TaskExecutionType.PERSONAL);
  assertInstanceOf(BusinessException.class,serialized("TodoMapper.insert",()->{as("MANAGER");tasks.assign(task,target("STAFF"));},()->{as("OWNER");tasks.assign(task,target("STAFF"));}));
  assertEquals(1,count(task,"todo"));
 }
 @Test void concurrentParentForceCompleteCannotRaceSubtaskCreation(){
  long parent=general(TaskExecutionType.GROUP_GROUP);
  assertInstanceOf(BusinessException.class,serialized("HotelTaskMapper.insert",()->{as("MANAGER");tasks.subtask(parent,req(TaskExecutionType.PERSONAL,"child_001"));},()->{as("OWNER");tasks.forceComplete(parent,null);}));
  assertEquals(0,taskStatus(parent));
 }
 @Test void databaseUniqueAndOwnershipConstraints(){
  long task=general(TaskExecutionType.PERSONAL);assign(task,"STAFF");
  assertThrows(Exception.class,()->jdbc.update("INSERT INTO todo(task_id,assignment_id,user_id) SELECT task_id,assignment_id,user_id FROM todo WHERE task_id=?",task));
  assertThrows(Exception.class,()->jdbc.update("UPDATE todo SET user_id=? WHERE task_id=?",uid("OWNER"),task));
  assertThrows(Exception.class,()->jdbc.update("INSERT INTO task_assignment(task_id,assignee_user_id,status,assignment_round,is_current,assigned_by) VALUES(?,?,0,99,1,?)",task,uid("STAFF"),uid("MANAGER")));
 }
 @ParameterizedTest @ValueSource(booleans={true,false})
 void turnoverTodoKeepsMaintenance(boolean claim){
  long b=stay();pay(b,"300");clock.day(3);checkout(b);long task=jdbc.queryForObject("SELECT task_id FROM room_turnover_task WHERE booking_id=?",Long.class,b);
  if(claim){as("STAFF");tasks.claim(task);}else assign(task,"STAFF");
  as("STAFF");assertEquals(0,todoService.get(my(task)).getStatus());ack("STAFF",task);done("STAFF",task);assertEquals(2,taskStatus(task));assertEquals(3,jdbc.queryForObject("SELECT status FROM room WHERE id=?",Integer.class,room1));
 }
}
