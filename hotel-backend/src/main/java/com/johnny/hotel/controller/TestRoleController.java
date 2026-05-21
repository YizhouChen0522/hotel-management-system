package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/roles")
public class TestRoleController {
    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<String> customerOnly() {
        return Result.success("Only CUSTOMER can access this API");
    }
    @GetMapping("/staff-basic")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<String> staffBasic() {
        return Result.success("Staff basic function");
    }

    @GetMapping("/hr-approval")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<String> hrApproval() {
        return Result.success("HR can approve staff applications");
    }

    @GetMapping("/manager-report")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<String> managerReport() {
        return Result.success("Manager can view financial reports");
    }

    @GetMapping("/owner-management")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<String> ownerManagement() {
        return Result.success("Owner-level management function");
    }

    @GetMapping("/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<String> superAdminOnly() {
        return Result.success("Super admin only function");
    }

}
