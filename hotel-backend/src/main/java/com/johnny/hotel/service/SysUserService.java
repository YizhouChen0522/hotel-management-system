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
    UserVO visibleUserById(Long id, Long actorId);
    UserVO getUserByEmail(String email);
    UserVO registerCustomer(RegisterCustomerRequest request);
    LoginVO login(LoginRequest request);
    LoginVO loginCustomer(LoginRequest request);
    LoginVO loginInternal(LoginRequest request);
    UserVO registerEmployee(RegisterEmployeeRequest request);
    List<PendingUserVO> getPendingUsers();
    com.johnny.hotel.pagination.PageResult<PendingUserVO> getPendingUsers(Integer page,Integer size);
    void approveUser(Long userId,Long currentUserId);
    void rejectUser(Long userId, Long currentUserId);
    void enableUser(Long userId, Long currentUserId);
    void disableUser(Long userId, Long currentUserId);
}
