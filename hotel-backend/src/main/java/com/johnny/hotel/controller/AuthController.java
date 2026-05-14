package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService sysUserService;

    public AuthController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @PostMapping("/register/customer")
    public Result<SysUser> registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
        SysUser user = sysUserService.registerCustomer(request);
        return Result.success(user);
    }
}