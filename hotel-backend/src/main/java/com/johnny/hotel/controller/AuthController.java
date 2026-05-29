package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.dto.LoginRequest;
import com.johnny.hotel.dto.RegisterEmployeeRequest;
import com.johnny.hotel.vo.LoginVO;
import com.johnny.hotel.vo.UserVO;
import com.johnny.hotel.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.johnny.hotel.util.JwtUtil;
import com.johnny.hotel.entity.SysUser;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService sysUserService;
    private final JwtUtil jwtUtil;

    public AuthController(SysUserService sysUserService, JwtUtil jwtUtil) {
        this.sysUserService = sysUserService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register/customer")
    public Result<UserVO> registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
        UserVO user = sysUserService.registerCustomer(request);
        return Result.success(user);
    }
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        LoginVO user = sysUserService.login(request);
        return Result.success(user);
    }
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error(401, "Not logged in");
        }
        String email = authentication.getName();
        UserVO user = sysUserService.getUserByEmail(email);
        return Result.success(user);
    }
    @PostMapping("/register/employee")
    public Result<UserVO> registerEmployee(@Valid @RequestBody RegisterEmployeeRequest request) {
        return Result.success(sysUserService.registerEmployee(request));
    }

}