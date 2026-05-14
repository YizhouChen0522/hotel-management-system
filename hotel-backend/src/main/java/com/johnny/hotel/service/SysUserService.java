package com.johnny.hotel.service;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.dto.RegisterCustomerRequest;


public interface SysUserService {
    SysUser getUserById(Long id);
    SysUser registerCustomer(RegisterCustomerRequest request);
}
