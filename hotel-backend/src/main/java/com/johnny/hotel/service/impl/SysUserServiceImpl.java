package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.dto.LoginRequest;
import com.johnny.hotel.entity.SysRole;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.mapper.SysRoleMapper;
import com.johnny.hotel.mapper.SysUserMapper;
import com.johnny.hotel.mapper.SysUserRoleMapper;
import com.johnny.hotel.service.SysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.johnny.hotel.vo.UserVO;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper sysUserMapper,
                              SysRoleMapper sysRoleMapper,
                              SysUserRoleMapper sysUserRoleMapper,
                              PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SysUser getUserById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    @Transactional
    public UserVO registerCustomer(RegisterCustomerRequest request) {
        SysUser existingUser = sysUserMapper.selectByUsername(request.getUsername());

        if (existingUser != null) {
            throw new RuntimeException("Username already exists");
        }

        SysUser existingEmailUser = sysUserMapper.selectByEmail(request.getEmail());

        if (existingEmailUser != null) {
            throw new RuntimeException("Email already exists");
        }

        SysRole customerRole = sysRoleMapper.selectByRoleCode("CUSTOMER");

        if (customerRole == null) {
            throw new RuntimeException("CUSTOMER role does not exist");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        SysUser user = SysUser.builder()
                .username(request.getUsername())
                .password(encodedPassword)
                .realName(request.getRealName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .status(1)
                .build();

        sysUserMapper.insert(user);

        sysUserRoleMapper.insertUserRole(user.getId(), customerRole.getId());

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .build();
    }
    @Override
    public UserVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectByEmail(request.getEmail());

        if (user == null) {
            throw new RuntimeException("email cannot be empty");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("Account is not active");
        }
        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new RuntimeException("Invalid email or password");
        }

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .build();
    }
}
