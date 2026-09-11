package com.johnny.hotel.wallet;

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
public class WalletAccess {
    private final SysUserMapper users;
    private final SysRoleMapper roles;

    public Long actor() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Long id)) throw denied();
        var user=users.selectById(id);
        if(user==null || !Integer.valueOf(1).equals(user.getStatus())) throw denied();
        return id;
    }
    private Set<String> roles(Long id) {
        return roles.selectRolesByUserId(id).stream().map(r->r.getRoleCode()).collect(Collectors.toSet());
    }
    public void read(Long actor,Wallet wallet) {
        if(wallet==null) throw denied();
        if(actor.equals(wallet.getUserId())) return;
        manage(actor,wallet);
    }
    public void own(Long actor,Wallet wallet) {
        if(wallet==null || !actor.equals(wallet.getUserId())) throw denied();
    }
    public void manage(Long actor,Wallet wallet) {
        if(wallet==null) throw denied();
        var actorRoles=roles(actor);
        if(actorRoles.contains("SUPER_ADMIN")) return;
        // Mixed roles are deliberately classified conservatively. HR finance access is never inferred from HR duties.
        if(actorRoles.contains("HR_ADMIN") || !roles(wallet.getUserId()).equals(Set.of("CUSTOMER"))) throw denied();
        var target=users.selectById(wallet.getUserId());
        if(target==null || target.getApplyRoleCode()!=null && !"CUSTOMER".equals(target.getApplyRoleCode())) throw denied();
        if(actorRoles.stream().noneMatch(Set.of("STAFF","MANAGER","OWNER")::contains)) throw denied();
    }
    public void review(Long actor,Wallet wallet) {
        manage(actor,wallet);
        if(actor.equals(wallet.getUserId())) throw denied();
    }
    private AccessDeniedException denied() {return new AccessDeniedException("Wallet access denied");}
}
