package com.johnny.hotel.ar;
import com.johnny.hotel.entity.*;import com.johnny.hotel.mapper.*;import lombok.*;import org.springframework.security.access.AccessDeniedException;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.stereotype.Component;import java.util.*;import java.util.stream.*;
@Component @RequiredArgsConstructor public class ArAccess {private final SysUserMapper users;private final SysRoleMapper roles;public record Actor(Long id,Set<String> roles){}
 public Actor actor(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||!(a.getDetails() instanceof Long id))throw denied();var u=users.selectById(id);if(u==null||!Integer.valueOf(1).equals(u.getStatus()))throw denied();var rs=roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());if(rs.stream().noneMatch(Set.of("FINANCE","OWNER","SUPER_ADMIN")::contains)||rs.contains("CUSTOMER")||rs.contains("HR_ADMIN"))throw denied();return new Actor(id,rs);}
 private AccessDeniedException denied(){return new AccessDeniedException("Accounts receivable access denied");}
}
