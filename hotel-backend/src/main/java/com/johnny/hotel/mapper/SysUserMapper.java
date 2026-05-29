package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysUserMapper {
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser selectById(Long id);

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE email = #{email}")
    SysUser selectByEmail(String email);

    @Insert("""
        INSERT INTO sys_user (
            username,
            password,
            real_name,
            phone,
            email,
            status,
            apply_role_code,
            apply_reason,
            create_time,
            update_time
        )
        VALUES (
            #{username},
            #{password},
            #{realName},
            #{phone},
            #{email},
            #{status},
            #{applyRoleCode},
            #{applyReason},
            NOW(),
            NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Select("SELECT * FROM sys_user WHERE status = 2")
    List<SysUser> selectPendingUsers();

    @Update("UPDATE sys_user SET status = #{status}, update_time = Now() WHERE id = #{id}")
    int updateUserStatus(@Param("id") Long id, @Param("status") Integer status);
}
