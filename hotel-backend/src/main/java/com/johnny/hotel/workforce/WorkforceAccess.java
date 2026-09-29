package com.johnny.hotel.workforce;
import com.johnny.hotel.entity.*;import com.johnny.hotel.mapper.*;import lombok.RequiredArgsConstructor;import org.springframework.security.access.AccessDeniedException;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.stereotype.Component;import java.util.*;import java.util.stream.Collectors;
@Component @RequiredArgsConstructor public class WorkforceAccess {
 private final SysUserMapper users;private final SysRoleMapper roles;
 public record Actor(Long id,Long departmentId,Set<String> roles){boolean has(String r){return roles.contains(r);}boolean any(String...r){return Arrays.stream(r).anyMatch(roles::contains);}}
 public Actor actor(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||!(a.getDetails() instanceof Long id))throw denied();return employee(id);}
 public Actor employee(Long id){var u=id==null?null:users.selectById(id);Set<String> rs=id==null?Set.of():roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());if(u==null||!Integer.valueOf(1).equals(u.getStatus())||rs.contains("CUSTOMER")||rs.stream().noneMatch(Set.of("STAFF","MANAGER","OWNER","SUPER_ADMIN","HR_ADMIN","FINANCE")::contains))throw denied();return new Actor(id,u.getDepartmentId(),rs);}
 public void hrOrSystem(Actor a){if(!a.any("HR_ADMIN","OWNER","SUPER_ADMIN"))throw denied();}
 public void system(Actor a){if(!a.any("OWNER","SUPER_ADMIN"))throw denied();}
 public AccessDeniedException denied(){return new AccessDeniedException("Workforce access denied");}
}
