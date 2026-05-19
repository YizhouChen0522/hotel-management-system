package com.johnny.hotel.service;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.dto.LoginRequest;
import com.johnny.hotel.vo.UserVO;
import com.johnny.hotel.vo.LoginVO;

public interface SysUserService {
    SysUser getUserById(Long id);
    UserVO getUserByEmail(String email);
    UserVO registerCustomer(RegisterCustomerRequest request);
    LoginVO login(LoginRequest request);
}
