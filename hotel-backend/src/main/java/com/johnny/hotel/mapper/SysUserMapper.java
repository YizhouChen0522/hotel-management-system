package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SysUserMapper {
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser selectById(Long id);

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(String username);

    @Insert("""
            INSERT INTO sys_user (username, password, real_name, phone, email, status)
            VALUES (#{username}, #{password}, #{realName}, #{phone}, #{email}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);
}
