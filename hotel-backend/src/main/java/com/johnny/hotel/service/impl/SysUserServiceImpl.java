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
import com.johnny.hotel.vo.LoginVO;
import com.johnny.hotel.util.JwtUtil;
import com.johnny.hotel.exception.BusinessException;

import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public SysUserServiceImpl(SysUserMapper sysUserMapper,
                              SysRoleMapper sysRoleMapper,
                              SysUserRoleMapper sysUserRoleMapper,
                              PasswordEncoder passwordEncoder,
                              JwtUtil JwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = JwtUtil;
    }

    @Override
    public SysUser getUserById(Long id) {
        return sysUserMapper.selectById(id);
    }
    @Override
    public UserVO getUserByEmail(String email) {
        SysUser user = sysUserMapper.selectByEmail(email);
        if (user == null) {
            throw new BusinessException("User not found");
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
    @Override
    @Transactional
    public UserVO registerCustomer(RegisterCustomerRequest request) {
        SysUser existingUser = sysUserMapper.selectByUsername(request.getUsername());

        if (existingUser != null) {
            throw new BusinessException("Username already exists");
        }

        SysUser existingEmailUser = sysUserMapper.selectByEmail(request.getEmail());

        if (existingEmailUser != null) {
            throw new BusinessException("Email already exists");
        }

        SysRole customerRole = sysRoleMapper.selectByRoleCode("CUSTOMER");

        if (customerRole == null) {
            throw new BusinessException("CUSTOMER role does not exist");
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
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectByEmail(request.getEmail());

        if (user == null) {
            throw new BusinessException("User doesn't exist");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("Account is not active");
        }
        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new BusinessException("Invalid email or password");
        }
        List<SysRole> roleList = sysRoleMapper.selectRolesByUserId(user.getId());

        List<String> roles = roleList.stream()
                .map(SysRole::getRoleCode)
                .toList();

        UserVO userVO = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .build();

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                roles
        );
        return LoginVO.builder()
                .token(token)
                .user(userVO)
                .roles(roles)
                .build();
    }
}
