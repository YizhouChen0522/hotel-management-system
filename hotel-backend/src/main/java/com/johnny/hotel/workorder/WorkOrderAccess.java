package com.johnny.hotel.workorder;
import com.johnny.hotel.entity.*;import com.johnny.hotel.mapper.*;import com.johnny.hotel.organization.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;import org.springframework.security.access.AccessDeniedException;import org.springframework.security.core.context.SecurityContextHolder;import java.util.*;import java.util.stream.Collectors;
@Component @RequiredArgsConstructor public class WorkOrderAccess {
 private final SysUserMapper users;private final SysRoleMapper roles;private final OrganizationMapper organization;
 public record Actor(Long id,Set<String> roles,boolean departmentManager){public boolean manager(){return roles.stream().anyMatch(Set.of("MANAGER","OWNER","SUPER_ADMIN")::contains);}}
 public Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getDetails() instanceof Long id))throw denied();var u=users.selectById(id);var rs=roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());if(u==null||!Integer.valueOf(1).equals(u.getStatus())||rs.contains("CUSTOMER")||rs.contains("HR_ADMIN")||rs.stream().noneMatch(Set.of("STAFF","MANAGER","OWNER","SUPER_ADMIN")::contains))throw denied();return new Actor(id,rs,organization.managed(id)!=null);}
 public void manager(Actor a){if(!a.manager())throw denied();}private AccessDeniedException denied(){return new AccessDeniedException("Room maintenance access denied");}
}
