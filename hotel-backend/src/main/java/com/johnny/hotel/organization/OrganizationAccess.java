package com.johnny.hotel.organization;
import com.johnny.hotel.mapper.*;import com.johnny.hotel.entity.SysRole;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;import java.util.stream.Collectors;
@Component @RequiredArgsConstructor public class OrganizationAccess {
 private final SysUserMapper users;private final SysRoleMapper roles;
 public record Actor(Long id,Set<String> roles){public boolean has(String role){return roles.contains(role);}}
 public Actor actor(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||!(a.getDetails() instanceof Long id))throw denied();return employee(id);}
 public Actor employee(Long id){if(id==null)throw denied();var u=users.selectById(id);var r=roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
  if(u==null||!Integer.valueOf(1).equals(u.getStatus())||r.contains("CUSTOMER")||r.stream().noneMatch(Set.of("STAFF","MANAGER","OWNER","HR_ADMIN","SUPER_ADMIN")::contains))throw denied();return new Actor(id,r);}
 public void member(Long id){if(id==null)throw denied();var u=users.selectById(id);var r=roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
  if(u==null||u.getStatus()==null||!Set.of(0,1).contains(u.getStatus())||r.contains("CUSTOMER")||r.stream().noneMatch(Set.of("STAFF","MANAGER","OWNER","HR_ADMIN","SUPER_ADMIN")::contains))throw denied();}
 public void hr(Actor a){if(!a.has("HR_ADMIN"))throw denied();}
 public boolean oversight(Actor a){return a.has("HR_ADMIN")||a.has("OWNER")||a.has("MANAGER")||a.has("SUPER_ADMIN");}
 public AccessDeniedException denied(){return new AccessDeniedException("Organization access denied");}
}
