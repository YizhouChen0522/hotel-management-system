package com.johnny.hotel.controller;
import com.johnny.hotel.common.Result;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.service.SysUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping("/{id}")
    public Result<SysUser> getUserById(@PathVariable Long id) {
        SysUser user = sysUserService.getUserById(id);

        if (user == null) {
            return Result.error(404, "User not found");
        }

        return Result.success(user);
    }
}