package com.johnny.hotel.organization;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.task.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.johnny.hotel.organization.OrganizationRequestType.*;

@Service @RequiredArgsConstructor
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OrganizationService {
 private final OrganizationMapper db;private final OrganizationAccess access;private final OrganizationTaskService workflow;private final SysAuditLogMapper audits;
 // Low-volume organization writes share a MySQL row lock, including manager account disable.
 // Order: organization_guard -> current organization/request data -> approval Task -> Assignment/Todo.
 private void lock(){if(db.guard()==null)throw new BusinessException("Organization guard missing");}
 private void require(boolean ok,String message){if(!ok)throw new BusinessException(message);}
 private void one(int rows){require(rows==1,"Organization state changed");}
 private String text(String v,int length,boolean required){String s=v==null?null:v.trim();require(!required||s!=null&&!s.isEmpty(),"Reason/name/request key required");require(s==null||s.length()<=length,"Field too long");return s==null||s.isEmpty()?null:s;}
 private OrganizationChangeRequest found(Long id){var r=db.request(id);if(r==null)throw new BusinessException(404,"Organization request not found");return r;}
 private Department department(Long id){var d=id==null?null:db.department(id);require(d!=null,"Department not found");return d;}
 private SysUser employee(Long id){access.employee(id);return db.user(id);}
 private void permission(OrganizationAccess.Actor actor,OrganizationRequestType type,Department dept){
  if(actor.has("SUPER_ADMIN"))return;
  switch(type){
   case CREATE_DEPARTMENT,DISABLE_DEPARTMENT -> {if(!actor.has("MANAGER")&&!actor.has("OWNER"))throw access.denied();}
   case CHANGE_DEPARTMENT_MANAGER -> {if(!actor.has("OWNER")&&!actor.has("HR_ADMIN"))throw access.denied();}
   case REMOVE_EMPLOYEE_FROM_DEPARTMENT,MOVE_EMPLOYEE_TO_DEPARTMENT -> {
    if(!access.oversight(actor)&&(dept==null||!actor.id().equals(dept.getManagerUserId())))throw access.denied();
   }
  }
 }
 private OrganizationChangeRequest prepare(OrganizationRequests.Change c,OrganizationAccess.Actor actor,boolean bypass){
  require(c!=null&&c.getType()!=null,"Request type required");var type=c.getType();
  String reason=text(c.getReason(),500,!bypass);
  Department d=type==CREATE_DEPARTMENT?null:department(c.getDepartmentId());permission(actor,type,d);
  var r=OrganizationChangeRequest.builder().requestedBy(actor.id()).requestType(type.getCode()).status(0).reason(reason).requestKey(text(c.getRequestKey(),100,!bypass)).targetDepartmentId(d==null?null:d.getId()).build();
  if(d!=null){require(d.getStatus()==1,"Department is disabled");r.setExpectedDepartmentVersion(d.getVersion());r.setExpectedManagerId(d.getManagerUserId());}
  switch(type){
   case CREATE_DEPARTMENT -> {require(c.getDepartmentId()==null&&c.getTargetUserId()==null&&c.getProposedManagerId()==null,"Unexpected target");r.setDepartmentName(text(c.getDepartmentName(),120,true));require(db.named(r.getDepartmentName())==null,"Department name has already been used");}
   case DISABLE_DEPARTMENT -> {require(c.getTargetUserId()==null&&c.getProposedManagerId()==null,"Unexpected employee");require(d.getManagerUserId()==null&&db.memberCount(d.getId())==0,"Department must have no employees and no manager");}
   case CHANGE_DEPARTMENT_MANAGER -> {
    require(c.getTargetUserId()==null,"Use proposedManagerId");r.setProposedManagerId(c.getProposedManagerId());
    require(!Objects.equals(d.getManagerUserId(),c.getProposedManagerId()),"Manager is unchanged");
    if(c.getProposedManagerId()!=null){var u=employee(c.getProposedManagerId());require(u.getDepartmentId()==null||u.getDepartmentId().equals(d.getId()),"Manager candidate must be unassigned or in this department");r.setExpectedCurrentDepartmentId(u.getDepartmentId());r.setExpectedUserVersion(u.getOrganizationVersion());}
   }
   case REMOVE_EMPLOYEE_FROM_DEPARTMENT,MOVE_EMPLOYEE_TO_DEPARTMENT -> {
    require(c.getProposedManagerId()==null,"Unexpected manager");access.member(c.getTargetUserId());var u=db.user(c.getTargetUserId());r.setTargetUserId(u.getId());r.setExpectedCurrentDepartmentId(u.getDepartmentId());r.setExpectedUserVersion(u.getOrganizationVersion());
    if(type==REMOVE_EMPLOYEE_FROM_DEPARTMENT){require(d.getId().equals(u.getDepartmentId()),"Employee is not in the source department");require(!u.getId().equals(d.getManagerUserId()),"Replace or remove the department manager first");r.setSourceDepartmentId(d.getId());}
    else require(u.getDepartmentId()==null,"Employee must first be removed from the current department");
   }
  }
  return r;
 }
 private boolean same(OrganizationChangeRequest r,OrganizationRequests.Change c){
  return c.getType()!=null&&r.getRequestType()==c.getType().getCode()&&Objects.equals(r.getTargetDepartmentId(),c.getDepartmentId())&&Objects.equals(r.getTargetUserId(),c.getTargetUserId())&&Objects.equals(r.getProposedManagerId(),c.getProposedManagerId())&&Objects.equals(r.getReason(),text(c.getReason(),500,true))&&Objects.equals(r.getDepartmentName(),text(c.getDepartmentName(),120,false));
 }
 public OrganizationChangeRequest create(OrganizationRequests.Change c){
  lock();var a=access.actor();require(c!=null,"Request required");String key=text(c.getRequestKey(),100,true);
  var old=db.byKey(a.id(),key);if(old!=null){require(same(old,c),"Request key already used");return old;}
  var r=prepare(c,a,false);r.setTaskId(workflow.create(r));one(db.insertRequest(r));audit(a.id(),r.getId(),"ORGANIZATION_REQUEST");return db.request(r.getId());
 }
 private OrganizationRequests.Change change(OrganizationChangeRequest r){
  return OrganizationRequests.Change.builder().type(OrganizationRequestType.fromCode(r.getRequestType())).requestKey(r.getRequestKey()).reason(r.getReason()).departmentName(r.getDepartmentName()).departmentId(r.getTargetDepartmentId()).targetUserId(r.getTargetUserId()).proposedManagerId(r.getProposedManagerId()).build();
 }
 private boolean current(OrganizationChangeRequest r){
  try{
   var n=prepare(change(r),access.employee(r.getRequestedBy()),false);
   return Objects.equals(n.getExpectedCurrentDepartmentId(),r.getExpectedCurrentDepartmentId())&&Objects.equals(n.getExpectedManagerId(),r.getExpectedManagerId())&&Objects.equals(n.getExpectedDepartmentVersion(),r.getExpectedDepartmentVersion())&&Objects.equals(n.getExpectedUserVersion(),r.getExpectedUserVersion());
  }catch(BusinessException|org.springframework.security.access.AccessDeniedException e){return false;}
 }
 private void approver(OrganizationChangeRequest r,OrganizationAccess.Actor a){
  access.hr(a);
  if(r.getRequestedBy().equals(a.id())&&r.getRequestType()!=3&&r.getRequestType()!=4)throw access.denied();
 }
 private void read(OrganizationChangeRequest r,OrganizationAccess.Actor a){if(!access.oversight(a)&&!r.getRequestedBy().equals(a.id()))throw new BusinessException(404,"Organization request not found");}
 public OrganizationChangeRequest get(Long id){var a=access.actor();var r=found(id);read(r,a);return r;}
 public List<OrganizationChangeRequest> requests(Integer page,Integer size){var a=access.actor();int[] p=page(page,size);return db.requests(access.oversight(a)?null:a.id(),p[0],p[1]);}
 private int[] page(Integer page,Integer size){int p=page==null?1:page,s=size==null?50:size;require(p>0&&s>0&&s<=100&&(long)(p-1)*s<=Integer.MAX_VALUE,"Invalid pagination");return new int[]{(p-1)*s,s};}
 public List<Department> departments(Integer page,Integer size){access.actor();int[] p=page(page,size);return db.departments(p[0],p[1]);}
 public Department departmentDetails(Long id){access.actor();return department(id);}
 public List<OrganizationChangeHistory> history(Integer page,Integer size){var a=access.actor();if(!access.oversight(a))throw access.denied();int[] p=page(page,size);return db.historyPage(p[0],p[1]);}
 public TaskView task(Long id){var a=access.actor();var r=found(id);read(r,a);return workflow.view(r.getTaskId());}
 public List<Todo> myTodos(){var a=access.actor();access.hr(a);return db.approvalTodos(a.id());}
 public OrganizationChangeRequest claim(Long id){lock();var a=access.actor();var r=found(id);approver(r,a);require(r.getStatus()==0,"Request is not pending");workflow.claim(r,a.id());return r;}
 public OrganizationChangeRequest approve(Long id){
  lock();var a=access.actor();var r=found(id);approver(r,a);if(r.getStatus()==1||r.getStatus()==4)return r;require(r.getStatus()==0,"Request cannot be approved");
  workflow.claim(r,a.id());
  if(!current(r)){one(db.resolve(id,4,a.id(),"Organization preconditions changed"));workflow.finish(r,a.id(),false);audit(a.id(),id,"ORGANIZATION_INVALIDATED");return db.request(id);}
  apply(r,a.id(),false);one(db.resolve(id,1,a.id(),r.getReason()));workflow.finish(r,a.id(),false);audit(a.id(),id,"ORGANIZATION_APPROVED");return db.request(id);
 }
 public OrganizationChangeRequest reject(Long id,String reason){
  lock();var a=access.actor();var r=found(id);approver(r,a);String why=text(reason,500,true);if(r.getStatus()==2)return r;require(r.getStatus()==0,"Request cannot be rejected");
  workflow.claim(r,a.id());one(db.resolve(id,2,a.id(),why));workflow.finish(r,a.id(),false);audit(a.id(),id,"ORGANIZATION_REJECTED");return db.request(id);
 }
 public OrganizationChangeRequest cancel(Long id,String reason){
  lock();var a=access.actor();var r=found(id);if(!a.id().equals(r.getRequestedBy())&&!a.has("SUPER_ADMIN"))throw access.denied();
  String why=text(reason,500,true);if(r.getStatus()==3)return r;require(r.getStatus()==0,"Request cannot be cancelled");one(db.resolve(id,3,a.id(),why));workflow.finish(r,a.id(),true);audit(a.id(),id,"ORGANIZATION_CANCELLED");return db.request(id);
 }
 public OrganizationChangeHistory direct(OrganizationRequests.Change c){lock();var a=access.actor();if(!a.has("SUPER_ADMIN"))throw access.denied();var r=prepare(c,a,true);var h=apply(r,a.id(),true);audit(a.id(),null,"ORGANIZATION_DIRECT");return h;}
 private OrganizationChangeHistory apply(OrganizationChangeRequest r,Long operator,boolean bypass){
  var type=OrganizationRequestType.fromCode(r.getRequestType());
  var h=OrganizationChangeHistory.builder().requestId(r.getId()).actionType(r.getRequestType()).targetUserId(r.getTargetUserId()).operatorUserId(operator).approverUserId(bypass?null:operator).reason(r.getReason()).bypassApproval(bypass).build();
  switch(type){
   case CREATE_DEPARTMENT -> {var d=Department.builder().name(r.getDepartmentName()).build();one(db.insertDepartment(d));h.setNewDepartmentId(d.getId());}
   case DISABLE_DEPARTMENT -> {var d=department(r.getTargetDepartmentId());one(db.disable(d.getId(),d.getVersion()));h.setOldDepartmentId(d.getId());h.setNewDepartmentId(d.getId());}
   case CHANGE_DEPARTMENT_MANAGER -> {
    var d=department(r.getTargetDepartmentId());Long candidate=r.getProposedManagerId();
    if(candidate!=null){var u=db.user(candidate);if(u.getDepartmentId()==null)one(db.membership(candidate,d.getId(),u.getOrganizationVersion()));}
    one(db.manager(d.getId(),candidate,d.getVersion()));h.setTargetUserId(candidate);h.setOldDepartmentId(r.getExpectedCurrentDepartmentId());h.setNewDepartmentId(d.getId());h.setOldManagerId(d.getManagerUserId());h.setNewManagerId(candidate);
   }
   case REMOVE_EMPLOYEE_FROM_DEPARTMENT,MOVE_EMPLOYEE_TO_DEPARTMENT -> {
    var u=db.user(r.getTargetUserId());Long destination=type==MOVE_EMPLOYEE_TO_DEPARTMENT?r.getTargetDepartmentId():null;
    one(db.membership(u.getId(),destination,u.getOrganizationVersion()));h.setOldDepartmentId(u.getDepartmentId());h.setNewDepartmentId(destination);
   }
  }
  one(db.history(h));return h;
 }
 private void audit(Long actor,Long request,String action){one(audits.insert(SysAuditLog.builder().operatorId(actor).action(action).detail("Organization request "+request).build()));}
}
