package com.johnny.hotel.workforce;

import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.organization.Department;
import com.johnny.hotel.workforce.WorkforceModels.ApprovalConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class WorkforceApprovalRouting {
 public static final String DEPARTMENT="DEPARTMENT_LEADER_DIRECT",HR_DEPARTMENT="HR_DEPARTMENT_LEADER_DIRECT",HR_POOL="HR_MANAGER_POOL",OWNER_DIRECT="OWNER_DIRECT",OWNER_POOL="OWNER_POOL",EXEMPT="EXEMPT";
 public record Route(String code,Long departmentId,Long approver,Set<String> eligibleRoles){public boolean pool(){return approver==null&&!eligibleRoles.isEmpty();}}
 private final WorkforceMapper mapper;private final WorkforceAccess access;private final SysAuditLogMapper audits;
 private void need(boolean ok,String message){if(!ok)throw new BusinessException(409,message);}
 private boolean active(Long user){try{access.employee(user);return true;}catch(RuntimeException ex){return false;}}
 @Transactional(propagation=Propagation.MANDATORY)
 public Route resolve(WorkforceAccess.Actor requester){
  if(requester.has("OWNER"))return new Route(EXEMPT,requester.departmentId(),null,Set.of());
  if(requester.any("HR_ADMIN","MANAGER")){
   var owners=mapper.activeUsersByRole("OWNER").stream().filter(id->!id.equals(requester.id())).distinct().toList();
   need(!owners.isEmpty(),"Workforce approval configuration has no active OWNER");
   return owners.size()==1?new Route(OWNER_DIRECT,requester.departmentId(),owners.get(0),Set.of()):new Route(OWNER_POOL,requester.departmentId(),null,Set.of("OWNER"));
  }
  need(requester.departmentId()!=null,"Workforce approval configuration: employee has no department");
  Department own=mapper.lockDepartment(requester.departmentId());
  need(own!=null&&Integer.valueOf(1).equals(own.getStatus())&&own.getManagerUserId()!=null&&active(own.getManagerUserId()),"Workforce approval configuration: department has no active leader");
  if(!own.getManagerUserId().equals(requester.id()))return new Route(DEPARTMENT,own.getId(),own.getManagerUserId(),Set.of());
  ApprovalConfig config=mapper.lockApprovalConfig();
  if(config!=null&&config.getHrDepartmentId()!=null){Department hr=mapper.lockDepartment(config.getHrDepartmentId());if(hr!=null&&Integer.valueOf(1).equals(hr.getStatus())&&hr.getManagerUserId()!=null&&!hr.getManagerUserId().equals(requester.id())&&active(hr.getManagerUserId()))return new Route(HR_DEPARTMENT,own.getId(),hr.getManagerUserId(),Set.of());}
  return new Route(HR_POOL,own.getId(),null,Set.of("HR_ADMIN","MANAGER"));
 }
 public void requireNotExempt(Route route,String domain){need(!EXEMPT.equals(route.code()),domain+" is exempt for OWNER accounts");}
 @Transactional public ApprovalConfig config(){var a=access.actor();access.hrOrSystem(a);return mapper.lockApprovalConfig();}
 @Transactional public ApprovalConfig configure(Long hrDepartmentId){var a=access.actor();access.hrOrSystem(a);if(hrDepartmentId!=null){var d=mapper.lockDepartment(hrDepartmentId);need(d!=null&&Integer.valueOf(1).equals(d.getStatus()),"HR department must be active");}var c=ApprovalConfig.builder().id(1).hrDepartmentId(hrDepartmentId).updatedBy(a.id()).build();mapper.saveApprovalConfig(c);need(audits.insert(SysAuditLog.builder().operatorId(a.id()).action("WORKFORCE_APPROVAL_CONFIG_UPDATED").detail("HR department "+hrDepartmentId).build())==1,"Audit write failed");return mapper.lockApprovalConfig();}
}
