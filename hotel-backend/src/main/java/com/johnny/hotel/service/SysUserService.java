package com.johnny.hotel.service;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.dto.LoginRequest;
import com.johnny.hotel.vo.UserVO;

public interface SysUserService {
    SysUser getUserById(Long id);
    UserVO registerCustomer(RegisterCustomerRequest request);
    UserVO login(LoginRequest request);
}
