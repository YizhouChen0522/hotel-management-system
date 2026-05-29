package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.service.SysUserService;
import com.johnny.hotel.vo.PendingUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserApprovalController {

    private final SysUserService sysUserService;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<PendingUserVO>> getPendingUsers() {
        return Result.success(sysUserService.getPendingUsers());
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<Void> approveUser(@PathVariable Long userId) {
        sysUserService.approveUser(userId);
        return Result.success();
    }

    @PostMapping("/{userId}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<Void> rejectUser(@PathVariable Long userId) {
        sysUserService.rejectUser(userId);
        return Result.success();
    }
}
