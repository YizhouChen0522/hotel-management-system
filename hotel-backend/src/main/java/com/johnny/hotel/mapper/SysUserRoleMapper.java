package com.johnny.hotel.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface SysUserRoleMapper {
    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            """)
    int insertUserRole(Long userId, Long roleId);
}

