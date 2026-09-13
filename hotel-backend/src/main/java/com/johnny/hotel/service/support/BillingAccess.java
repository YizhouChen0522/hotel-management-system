package com.johnny.hotel.service.support;

import com.johnny.hotel.entity.SysRole;
import com.johnny.hotel.mapper.SysRoleMapper;
import com.johnny.hotel.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BillingAccess {
    private static final Set<String> OPERATIONS = Set.of("STAFF", "MANAGER", "OWNER", "SUPER_ADMIN");
    private final SysUserMapper users;
    private final SysRoleMapper roles;

    public Long currentCustomer(Long assertedUserId) {
        Long actor = currentActor();
        if (!actor.equals(assertedUserId) || !roleCodes(actor).equals(Set.of("CUSTOMER"))) throw denied();
        return actor;
    }

    public Long currentOperational() {
        Long actor = currentActor();
        operational(actor);
        return actor;
    }

    public void operational(Long actor) {
        if (actor == null) throw denied();
        var user = users.selectById(actor);
        var codes = roleCodes(actor);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || codes.contains("HR_ADMIN")
                || codes.contains("CUSTOMER") || codes.stream().noneMatch(OPERATIONS::contains)) throw denied();
    }

    public java.util.List<String> operationalAuthorities(Long actor) {
        operational(actor);
        return roleCodes(actor).stream().map(code -> "ROLE_" + code).toList();
    }

    private Long currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Long id)) throw denied();
        var user = users.selectById(id);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw denied();
        return id;
    }

    private Set<String> roleCodes(Long id) {
        return roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
    }

    private AccessDeniedException denied() { return new AccessDeniedException("Billing access denied"); }
}
