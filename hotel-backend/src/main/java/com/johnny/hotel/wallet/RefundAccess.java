package com.johnny.hotel.wallet;
import com.johnny.hotel.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.stream.Collectors;

@Component @RequiredArgsConstructor
public class RefundAccess {
    private final WalletAccess walletAccess;
    private final SysRoleMapper roles;
    private final SysUserMapper users;
    public Long actor(){return walletAccess.actor();}
    private Set<String> roles(Long id){return roles.selectRolesByUserId(id).stream().map(r->r.getRoleCode()).collect(Collectors.toSet());}
    public void customer(Long id) {
        var u=users.selectById(id);
        if(u==null || !roles(id).equals(Set.of("CUSTOMER")) || u.getApplyRoleCode()!=null && !"CUSTOMER".equals(u.getApplyRoleCode())) throw denied();
    }
    public void read(Long actor,Long owner) {customer(owner);if(actor.equals(owner))return;operational(actor,false);}
    public void create(Long actor,Long owner){customer(owner);if(!actor.equals(owner))throw denied();}
    public void approve(Long actor,Long owner){customer(owner);if(actor.equals(owner))throw denied();operational(actor,true);}
    public void operational(Long actor,boolean approval) {
        var u=users.selectById(actor);var r=roles(actor);
        if(u==null || !Integer.valueOf(1).equals(u.getStatus()))throw denied();
        if(r.contains("SUPER_ADMIN"))return;
        if(r.contains("HR_ADMIN") || r.stream().noneMatch((approval?Set.of("MANAGER","OWNER"):Set.of("STAFF","MANAGER","OWNER"))::contains))throw denied();
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Refund billing access denied");}
}
