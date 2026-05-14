package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.entity.SysRole;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.mapper.SysRoleMapper;
import com.johnny.hotel.mapper.SysUserMapper;
import com.johnny.hotel.mapper.SysUserRoleMapper;
import com.johnny.hotel.service.SysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserServiceImpl(SysUserMapper sysUserMapper,
                              SysRoleMapper sysRoleMapper,
                              SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public SysUser getUserById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    @Transactional
    public SysUser registerCustomer(RegisterCustomerRequest request) {
        SysUser existingUser = sysUserMapper.selectByUsername(request.getUsername());

        if (existingUser != null) {
            throw new RuntimeException("Username already exists");
        }

        SysRole customerRole = sysRoleMapper.selectByRoleCode("CUSTOMER");

        if (customerRole == null) {
            throw new RuntimeException("CUSTOMER role does not exist");
        }

        SysUser user = SysUser.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .realName(request.getRealName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .status(1)
                .build();

        sysUserMapper.insert(user);

        sysUserRoleMapper.insertUserRole(user.getId(), customerRole.getId());

        return user;
    }
}
