package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.dto.LoginRequest;
import com.johnny.hotel.vo.UserVO;
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
    public Result<UserVO> registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
        UserVO user = sysUserService.registerCustomer(request);
        return Result.success(user);
    }
    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody LoginRequest request) {
        UserVO user = sysUserService.login(request);
        return Result.success(user);
    }
}