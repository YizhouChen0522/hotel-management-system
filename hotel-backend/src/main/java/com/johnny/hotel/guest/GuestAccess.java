package com.johnny.hotel.guest;
import com.johnny.hotel.entity.*;import com.johnny.hotel.mapper.*;import lombok.RequiredArgsConstructor;import org.springframework.security.access.AccessDeniedException;import org.springframework.stereotype.Component;import java.util.*;import java.util.stream.*;
@Component @RequiredArgsConstructor public class GuestAccess {private final SysUserMapper users;private final SysRoleMapper roles;
 public Set<String> activeRoles(Long id){var u=users.selectById(id);if(u==null||!Integer.valueOf(1).equals(u.getStatus()))throw denied();return roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());}
 public void employee(Long id){var r=activeRoles(id);if(r.contains("CUSTOMER")||r.contains("HR_ADMIN")||r.stream().noneMatch(Set.of("STAFF","MANAGER","OWNER","SUPER_ADMIN")::contains))throw denied();}
 public void customer(Long id){if(!activeRoles(id).contains("CUSTOMER"))throw denied();}
 private AccessDeniedException denied(){return new AccessDeniedException("Guest access denied");}
}
