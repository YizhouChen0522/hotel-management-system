package com.johnny.hotel.controller;
import com.johnny.hotel.common.Result;
import com.johnny.hotel.vo.UserVO;
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
    public Result<UserVO> getUserById(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        return Result.success(sysUserService.visibleUserById(id, (Long) authentication.getDetails()));
    }
}
