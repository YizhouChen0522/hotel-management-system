package com.johnny.hotel.mapper;

import com.johnny.hotel.entity.SysAuditLog;
import org.apache.ibatis.annotations.*;

import java.util.List;


@Mapper
public interface SysAuditLogMapper {

    @Insert("""
            INSERT INTO sys_audit_log (
                operator_id,
                target_user_id,
                action,
                detail,
                create_time
            )
            VALUES (
                #{operatorId},
                #{targetUserId},
                #{action},
                #{detail},
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysAuditLog auditLog);

    @Select("""
            SELECT *
            FROM sys_audit_log
            ORDER BY create_time DESC
            LIMIT 50
            """)
    List<SysAuditLog> selectLatestLogs();

    @Select("""
            SELECT *
            FROM sys_audit_log
            WHERE target_user_id = #{userId}
            ORDER BY create_time DESC
            LIMIT 10
            """)
    List<SysAuditLog> selectLogsByTargetUserId(@Param("userId") Long userId);
    @Select("""
            SELECT *
            FROM sys_audit_log
            WHERE operator_id = #{userId}
            ORDER BY create_time DESC
            LIMIT 10
            """)
    List<SysAuditLog> selectLogsByOperatorId(@Param("userId") Long userId);
}
