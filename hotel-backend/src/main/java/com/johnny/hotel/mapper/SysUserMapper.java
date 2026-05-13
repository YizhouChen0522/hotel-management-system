package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper {

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser selectById(Long id);
}
