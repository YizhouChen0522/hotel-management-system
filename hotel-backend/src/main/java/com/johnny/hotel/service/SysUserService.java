package com.johnny.hotel.service;
import com.johnny.hotel.dto.RegisterEmployeeRequest;
import com.johnny.hotel.entity.SysUser;
import com.johnny.hotel.dto.RegisterCustomerRequest;
import com.johnny.hotel.dto.LoginRequest;
import com.johnny.hotel.vo.PendingUserVO;
import com.johnny.hotel.vo.UserVO;
import com.johnny.hotel.vo.LoginVO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SysUserService {
    SysUser getUserById(Long id);
    UserVO getUserByEmail(String email);
    UserVO registerCustomer(RegisterCustomerRequest request);
    LoginVO login(LoginRequest request);
    UserVO registerEmployee(RegisterEmployeeRequest request);
    List<PendingUserVO> getPendingUsers();
    void approveUser(Long userId,Long currentUserId);
    void rejectUser(Long userId, Long currentUserId);
    void enableUser(Long userId, Long currentUserId);
    void disableUser(Long userId, Long currentUserId);
}
