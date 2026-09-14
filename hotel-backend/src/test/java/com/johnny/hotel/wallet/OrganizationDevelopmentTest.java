package com.johnny.hotel.wallet;
import com.johnny.hotel.organization.*;import com.johnny.hotel.task.*;import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static com.johnny.hotel.organization.OrganizationRequestType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.wallet.dev.tests",matches="true")
class OrganizationDevelopmentTest extends FinancialDevelopmentFixture {
 @Autowired OrganizationService org;@Autowired OrganizationMapper organizations;@Autowired HotelTaskService tasks;@Autowired TodoService todoService;@Autowired org.flywaydb.core.Flyway flyway;
 @Autowired OrganizationTaskService organizationTasks;
 final List<Long> departments=new ArrayList<>();int keys;
 OrganizationRequests.Change change(OrganizationRequestType type,Long dept,Long user,Long manager){
  return OrganizationRequests.Change.builder().type(type).departmentId(dept).targetUserId(user).proposedManagerId(manager).reason("Organization test reason").requestKey("org_"+keys++).build();
 }
 long department(String suffix){as("SUPER_ADMIN");var r=change(CREATE_DEPARTMENT,null,null,null);r.setDepartmentName(run+"_"+suffix);r.setReason(null);var h=org.direct(r);departments.add(h.getNewDepartmentId());return h.getNewDepartmentId();}
 void direct(OrganizationRequestType type,long dept,Long user,Long manager){as("SUPER_ADMIN");org.direct(change(type,dept,user,manager));}
 Long membership(String role){return jdbc.queryForObject("SELECT department_id FROM sys_user WHERE id=?",Long.class,uid(role));}
 OrganizationChangeRequest request(String role,OrganizationRequestType type,long dept,Long user,Long manager){as(role);return org.create(change(type,dept,user,manager));}
 OrganizationChangeRequest approve(long id){as("HR_ADMIN");return org.approve(id);}
 @AfterEach void organizationCleanup(){
  gate.clear();
  for(long id:departments)jdbc.update("UPDATE department SET manager_user_id=NULL WHERE id=?",id);
  for(long id:created)jdbc.update("UPDATE sys_user SET department_id=NULL WHERE id=?",id);
  for(long id:created)jdbc.update("DELETE FROM organization_change_history WHERE operator_user_id=? OR request_id IN (SELECT id FROM organization_change_request WHERE requested_by=?)",id,id);
  for(long id:created)jdbc.update("DELETE FROM organization_change_request WHERE requested_by=?",id);
  for(long id:departments)jdbc.update("DELETE FROM department WHERE id=?",id);
 }
 @Test void migrationValidateAndExistingUsersRemainUnassigned(){flyway.validate();assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='17'",Integer.class));assertNull(membership("STAFF"));assertEquals(0,OrganizationRequestStatus.PENDING_APPROVAL.getCode());assertEquals(4,OrganizationRequestStatus.INVALIDATED.getCode());}
 @Test void managerRequestsCreateHrApprovesAndHistoryExists(){
  as("MANAGER");var c=change(CREATE_DEPARTMENT,null,null,null);c.setDepartmentName(run+"_approved");var r=org.create(c);assertEquals(0,r.getStatus());assertNull(organizations.named(c.getDepartmentName()));
  assertEquals(r.getId(),org.create(c).getId());assertEquals(1,approve(r.getId()).getStatus());var d=organizations.named(c.getDepartmentName());departments.add(d.getId());assertNull(d.getManagerUserId());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_history WHERE request_id=? AND bypass_approval=0",Integer.class,r.getId()));
  assertEquals(2,jdbc.queryForObject("SELECT status FROM hotel_task WHERE id=?",Integer.class,r.getTaskId()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE task_id=? AND is_active=1 AND status<>2",Integer.class,r.getTaskId()));
  assertEquals(1,approve(r.getId()).getStatus());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_history WHERE request_id=?",Integer.class,r.getId()));
 }
 @Test void hrCannotCreateAndManagerOwnerCannotDirect(){var c=change(CREATE_DEPARTMENT,null,null,null);c.setDepartmentName(run+"_forbidden");as("HR_ADMIN");assertThrows(AccessDeniedException.class,()->org.create(c));for(String role:List.of("MANAGER","OWNER")){as(role);assertThrows(AccessDeniedException.class,()->org.direct(c));}}
 @Test void superDirectNoTaskNoReasonAndNameNeverReused(){
  long d=department("direct");assertEquals(1,organizations.department(d).getStatus());assertNull(organizations.department(d).getManagerUserId());direct(DISABLE_DEPARTMENT,d,null,null);assertEquals(0,organizations.department(d).getStatus());
  var c=change(CREATE_DEPARTMENT,null,null,null);c.setDepartmentName(run+"_direct");assertThrows(BusinessException.class,()->org.direct(c));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_request WHERE requested_by=?",Integer.class,uid("SUPER_ADMIN")));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_history WHERE operator_user_id=? AND bypass_approval=1",Integer.class,uid("SUPER_ADMIN")));
 }
 @Test void ownerManagerChangeAtomicallyJoinsCandidateWithoutChangingSystemRole(){
  long d=department("manager");var before=jdbc.queryForList("SELECT role_id FROM sys_user_role WHERE user_id=?",Long.class,uid("STAFF"));
  var r=request("OWNER",CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));assertEquals(1,approve(r.getId()).getStatus());assertEquals(d,membership("STAFF"));assertEquals(uid("STAFF"),organizations.department(d).getManagerUserId());assertEquals(before,jdbc.queryForList("SELECT role_id FROM sys_user_role WHERE user_id=?",Long.class,uid("STAFF")));
 }
 @Test void hrManagerChangeRequiresAnotherHr(){
  long d=department("hrmanager");var r=request("HR_ADMIN",CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));
  assertThrows(AccessDeniedException.class,()->org.approve(r.getId()));
  long other=users.registerEmployee(employeeRequest("HR_ADMIN")).getId();created.add(other);actors.put("OTHER_HR",other);jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",other);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='HR_ADMIN'",other);
  as("OTHER_HR");assertEquals(1,org.approve(r.getId()).getStatus());
 }
 @Test void managerCandidateMustBeActiveAndNotFromAnotherDepartment(){
  long a=department("a"),b=department("b");direct(MOVE_EMPLOYEE_TO_DEPARTMENT,a,uid("STAFF"),null);
  assertThrows(BusinessException.class,()->request("OWNER",CHANGE_DEPARTMENT_MANAGER,b,null,uid("STAFF")));
  jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",uid("OWNER"));assertThrows(AccessDeniedException.class,()->request("HR_ADMIN",CHANGE_DEPARTMENT_MANAGER,b,null,uid("OWNER")));
 }
 @Test void managerCannotMoveOrBeDisabledBeforeReplacement(){
  long d=department("manager");direct(CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));
  assertThrows(BusinessException.class,()->request("HR_ADMIN",REMOVE_EMPLOYEE_FROM_DEPARTMENT,d,uid("STAFF"),null));
  assertThrows(BusinessException.class,()->users.disableUser(uid("STAFF"),uid("SUPER_ADMIN")));
  direct(CHANGE_DEPARTMENT_MANAGER,d,null,null);var r=request("HR_ADMIN",REMOVE_EMPLOYEE_FROM_DEPARTMENT,d,uid("STAFF"),null);approve(r.getId());assertNull(membership("STAFF"));
 }
 @Test void departmentManagerScopeAndTwoStepCrossDepartmentMove(){
  long a=department("a"),b=department("b");direct(CHANGE_DEPARTMENT_MANAGER,a,null,uid("STAFF"));direct(CHANGE_DEPARTMENT_MANAGER,b,null,uid("MANAGER"));direct(MOVE_EMPLOYEE_TO_DEPARTMENT,a,uid("OWNER"),null);
  assertThrows(AccessDeniedException.class,()->request("STAFF",MOVE_EMPLOYEE_TO_DEPARTMENT,b,uid("SUPER_ADMIN"),null));
  assertThrows(BusinessException.class,()->request("MANAGER",MOVE_EMPLOYEE_TO_DEPARTMENT,b,uid("OWNER"),null));
  var remove=request("STAFF",REMOVE_EMPLOYEE_FROM_DEPARTMENT,a,uid("OWNER"),null);approve(remove.getId());assertNull(membership("OWNER"));
  var move=request("MANAGER",MOVE_EMPLOYEE_TO_DEPARTMENT,b,uid("OWNER"),null);approve(move.getId());assertEquals(b,membership("OWNER"));
 }
 @Test void staffRoleAloneCannotManageDepartment(){
  long d=department("staff");assertThrows(AccessDeniedException.class,()->request("STAFF",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("OWNER"),null));
  assertThrows(AccessDeniedException.class,()->request("MANAGER",CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF")));
 }
 @Test void hrAdministrativeExecutionKeepsRequestTaskTodoHistory(){
  long d=department("admin");var r=request("HR_ADMIN",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);
  assertEquals(1,org.myTodos().stream().filter(t->t.getTaskId().equals(r.getTaskId())).count());assertNull(membership("STAFF"));
  approve(r.getId());assertEquals(d,membership("STAFF"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_history WHERE request_id=? AND operator_user_id=? AND approver_user_id=?",Integer.class,r.getId(),uid("HR_ADMIN"),uid("HR_ADMIN")));
 }
 @Test void staleRequestInvalidatesWithoutOverwritingNewState(){
  long a=department("a"),b=department("b");var r=request("HR_ADMIN",MOVE_EMPLOYEE_TO_DEPARTMENT,a,uid("STAFF"),null);direct(MOVE_EMPLOYEE_TO_DEPARTMENT,b,uid("STAFF"),null);
  assertEquals(4,approve(r.getId()).getStatus());assertEquals(b,membership("STAFF"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_history WHERE request_id=?",Integer.class,r.getId()));
  assertEquals(2,jdbc.queryForObject("SELECT status FROM hotel_task WHERE id=?",Integer.class,r.getTaskId()));
 }
 @Test void disabledDepartmentRequiresNoManagerOrEmployees(){
  long d=department("disable");direct(MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);assertThrows(BusinessException.class,()->request("OWNER",DISABLE_DEPARTMENT,d,null,null));
  direct(REMOVE_EMPLOYEE_FROM_DEPARTMENT,d,uid("STAFF"),null);var r=request("OWNER",DISABLE_DEPARTMENT,d,null,null);assertEquals(1,approve(r.getId()).getStatus());assertEquals(0,organizations.department(d).getStatus());
 }
 @Test void rejectAndCancelDoNotChangeOrganization(){
  long d=department("reject");var r=request("OWNER",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);as("HR_ADMIN");assertThrows(BusinessException.class,()->org.reject(r.getId()," "));
  assertEquals(2,org.reject(r.getId(),"Not approved").getStatus());assertNull(membership("STAFF"));assertThrows(BusinessException.class,()->org.approve(r.getId()));
  var withdrawn=request("OWNER",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);assertEquals(3,org.cancel(withdrawn.getId(),"Withdrawn").getStatus());assertNull(membership("STAFF"));
 }
 @Test void pendingDisableBecomesInvalidWhenEmployeeArrives(){
  long d=department("stale");var r=request("OWNER",DISABLE_DEPARTMENT,d,null,null);direct(MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);assertEquals(4,approve(r.getId()).getStatus());assertEquals(1,organizations.department(d).getStatus());
 }
 @Test void approvalCannotBeCompletedViaGenericTaskEvenSuperAdmin(){
  long d=department("bound");var r=request("OWNER",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);as("SUPER_ADMIN");
  assertThrows(BusinessException.class,()->tasks.forceComplete(r.getTaskId(),null));assertThrows(BusinessException.class,()->tasks.cancel(r.getTaskId(),null));assertThrows(BusinessException.class,()->tasks.claim(r.getTaskId()));assertThrows(BusinessException.class,()->tasks.complete(r.getTaskId(),null));
  assertEquals(0,organizations.request(r.getId()).getStatus());assertNull(membership("STAFF"));
 }
 @Test void genericTodoCannotCompleteApproval(){
  long d=department("todo");var r=request("HR_ADMIN",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);long todo=org.myTodos().stream().filter(t->t.getTaskId().equals(r.getTaskId())).findFirst().orElseThrow().getId();
  assertThrows(AccessDeniedException.class,()->todoService.complete(todo,null));assertEquals(0,organizations.request(r.getId()).getStatus());
 }
 @Test void customerAndAnonymousHttpDenied()throws Exception{
  mvc.perform(get("/api/organization/departments").with(authentication(auth("CUSTOMER")))).andExpect(status().isForbidden());mvc.perform(get("/api/organization/departments").with(anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/organization/direct").with(authentication(auth("OWNER"))).contentType("application/json").content("{\"type\":\"CREATE_DEPARTMENT\",\"departmentName\":\"forbidden\"}")).andExpect(status().isForbidden());
 }
 @Test void httpHrApprovesAndCanReadScopedTask()throws Exception{
  long d=department("http");var r=request("OWNER",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);
  mvc.perform(get("/api/organization/requests/"+r.getId()+"/task").with(authentication(auth("HR_ADMIN")))).andExpect(status().isOk());
  mvc.perform(post("/api/organization/requests/"+r.getId()+"/approve").with(authentication(auth("MANAGER")))).andExpect(status().isForbidden());
  mvc.perform(post("/api/organization/requests/"+r.getId()+"/approve").with(authentication(auth("HR_ADMIN")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1));
 }
 @Test void concurrentMovesHaveOneApprovedAndOneInvalidated(){
  long a=department("a"),b=department("b");var x=request("OWNER",MOVE_EMPLOYEE_TO_DEPARTMENT,a,uid("STAFF"),null);var y=request("OWNER",MOVE_EMPLOYEE_TO_DEPARTMENT,b,uid("STAFF"),null);
  assertNull(serialized("OrganizationMapper.membership",()->approve(x.getId()),()->approve(y.getId())));
  assertEquals(a,membership("STAFF"));assertEquals(1,organizations.request(x.getId()).getStatus());assertEquals(4,organizations.request(y.getId()).getStatus());
 }
 @Test void concurrentManagersCannotBothBeApplied(){
  long d=department("managers");var x=request("OWNER",CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));var y=request("OWNER",CHANGE_DEPARTMENT_MANAGER,d,null,uid("MANAGER"));
  assertNull(serialized("OrganizationMapper.manager",()->approve(x.getId()),()->approve(y.getId())));
  assertEquals(uid("STAFF"),organizations.department(d).getManagerUserId());assertNull(membership("MANAGER"));assertEquals(4,organizations.request(y.getId()).getStatus());
 }
 @ParameterizedTest @ValueSource(strings={"OrganizationMapper.history","OrganizationMapper.resolve","TodoMapper.transition","SysAuditLogMapper.insert"})
 void approvalFailureRollsBackEverything(String statement){
  long d=department("rollback");var r=request("HR_ADMIN",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);as("HR_ADMIN");gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->org.approve(r.getId()));
  assertNull(membership("STAFF"));assertEquals(0,organizations.request(r.getId()).getStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_history WHERE request_id=?",Integer.class,r.getId()));assertNotEquals(2,jdbc.queryForObject("SELECT status FROM hotel_task WHERE id=?",Integer.class,r.getTaskId()));
 }
 @Test void managerMembershipAndManagerPointerRollbackTogether(){
  long d=department("atomic");var r=request("OWNER",CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));as("HR_ADMIN");gate.arm(Thread.currentThread().getName(),"OrganizationMapper.manager",true);assertThrows(Exception.class,()->org.approve(r.getId()));assertNull(membership("STAFF"));assertNull(organizations.department(d).getManagerUserId());
 }
 @Test void databaseRejectsManagerOutsideDepartment(){
  long d=department("fk");assertThrows(Exception.class,()->jdbc.update("UPDATE department SET manager_user_id=? WHERE id=?",uid("STAFF"),d));direct(CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));assertThrows(Exception.class,()->jdbc.update("UPDATE sys_user SET department_id=NULL WHERE id=?",uid("STAFF")));
 }
 @Test void allOrdinaryRequestsRequireReason(){
  long d=department("reason");as("OWNER");var c=change(MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);c.setReason(" ");assertThrows(BusinessException.class,()->org.create(c));
 }
 @Test void hrApprovalTodoQueryExcludesFormerOperationalWork(){
  long d=department("hr_scope");as("STAFF");var task=tasks.createGeneral(TaskRequests.CreateGeneral.builder().title("Old operational responsibility").requestKey("old_role").build());tasks.claim(task.getId());
  jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",uid("STAFF"));jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='HR_ADMIN'",uid("STAFF"));
  assertTrue(org.myTodos().isEmpty());
  var r=org.create(change(MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("OWNER"),null));assertEquals(1,org.myTodos().size());assertEquals(r.getTaskId(),org.myTodos().get(0).getTaskId());
 }
 @Test void internalTaskCompletionAlsoRequiresResolvedBusinessRequest(){
  long d=department("protected_completion");var r=request("HR_ADMIN",MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);
  assertThrows(BusinessException.class,()->new org.springframework.transaction.support.TransactionTemplate(txManager).executeWithoutResult(s->organizationTasks.finish(r,uid("HR_ADMIN"),false)));
  assertEquals(0,organizations.request(r.getId()).getStatus());assertNotEquals(2,jdbc.queryForObject("SELECT status FROM hotel_task WHERE id=?",Integer.class,r.getTaskId()));
 }
 @Test void disabledEmployeeCanLeaveDepartmentThroughApproval(){
  long d=department("disabled_member");direct(MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null);users.disableUser(uid("STAFF"),uid("SUPER_ADMIN"));
  var r=request("HR_ADMIN",REMOVE_EMPLOYEE_FROM_DEPARTMENT,d,uid("STAFF"),null);assertEquals(1,approve(r.getId()).getStatus());assertNull(membership("STAFF"));
 }
 @Test void concurrentlyDisablingNewManagerCannotLeaveInactiveManager(){
  long d=department("concurrent_disable");var r=request("OWNER",CHANGE_DEPARTMENT_MANAGER,d,null,uid("STAFF"));
  assertInstanceOf(BusinessException.class,serialized("OrganizationMapper.manager",()->approve(r.getId()),()->users.disableUser(uid("STAFF"),uid("SUPER_ADMIN"))));
  assertEquals(uid("STAFF"),organizations.department(d).getManagerUserId());assertEquals(1,jdbc.queryForObject("SELECT status FROM sys_user WHERE id=?",Integer.class,uid("STAFF")));
 }
 @ParameterizedTest @ValueSource(strings={"HotelTaskMapper.insert","TodoMapper.insert","OrganizationMapper.insertRequest"})
 void requestCreationFailureLeavesNoPartialTask(String statement){
  long d=department("create_failure");as("HR_ADMIN");int before=jdbc.queryForObject("SELECT COUNT(*) FROM hotel_task WHERE created_by=?",Integer.class,uid("HR_ADMIN"));
  gate.arm(Thread.currentThread().getName(),statement,true);assertThrows(Exception.class,()->org.create(change(MOVE_EMPLOYEE_TO_DEPARTMENT,d,uid("STAFF"),null)));
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM hotel_task WHERE created_by=?",Integer.class,uid("HR_ADMIN")));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM organization_change_request WHERE requested_by=?",Integer.class,uid("HR_ADMIN")));
 }
}
