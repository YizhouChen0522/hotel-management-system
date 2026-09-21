package com.johnny.hotel.reservation;

import com.johnny.hotel.entity.SysRole;
import com.johnny.hotel.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;

@Component @RequiredArgsConstructor
public class ReservationAccess {
    private final SysUserMapper users;
    private final SysRoleMapper roles;
    public record Actor(Long id, Set<String> roles) {
        public boolean customer(){return roles.equals(Set.of("CUSTOMER"));}
        public boolean hotel(){return !roles.contains("CUSTOMER")&&!roles.contains("HR_ADMIN")&&!roles.contains("FINANCE")
                && roles.stream().anyMatch(Set.of("STAFF","MANAGER","OWNER","SUPER_ADMIN")::contains);}
        public boolean owner(){return !roles.contains("CUSTOMER")&&!roles.contains("HR_ADMIN")
                && roles.stream().anyMatch(Set.of("OWNER","SUPER_ADMIN")::contains);}
        public boolean policyReader(){return hotel()||roles.contains("FINANCE");}
    }
    public Actor actor(){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||!(auth.getDetails() instanceof Long id))throw denied();
        var user=users.selectById(id);
        if(user==null||!Integer.valueOf(1).equals(user.getStatus()))throw denied();
        return new Actor(id,roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet()));
    }
    public Actor hotel(){var a=actor();if(!a.hotel())throw denied();return a;}
    public Actor owner(){var a=actor();if(!a.owner())throw denied();return a;}
    public Actor policyReader(){var a=actor();if(!a.policyReader())throw denied();return a;}
    public AccessDeniedException denied(){return new AccessDeniedException("Reservation access denied");}
}
