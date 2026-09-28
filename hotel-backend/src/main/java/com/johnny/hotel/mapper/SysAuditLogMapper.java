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
    @Select("""
            <script>SELECT id,operator_id,target_user_id,action,detail,create_time FROM sys_audit_log
            <where><if test='operatorId != null'>operator_id=#{operatorId}</if><if test='targetUserId != null'>AND target_user_id=#{targetUserId}</if><if test='action != null and action != ""'>AND action=#{action}</if><if test='from != null'>AND create_time&gt;=#{from}</if><if test='to != null'>AND create_time&lt;#{to}</if></where>
            ORDER BY create_time DESC,id DESC LIMIT #{offset},#{size}</script>
            """)
    List<SysAuditLog> searchPage(@Param("operatorId") Long operatorId,@Param("targetUserId") Long targetUserId,@Param("action") String action,@Param("from") java.time.LocalDateTime from,@Param("to") java.time.LocalDateTime to,@Param("offset") int offset,@Param("size") int size);
    @Select("""
            <script>SELECT COUNT(*) FROM sys_audit_log
            <where><if test='operatorId != null'>operator_id=#{operatorId}</if><if test='targetUserId != null'>AND target_user_id=#{targetUserId}</if><if test='action != null and action != ""'>AND action=#{action}</if><if test='from != null'>AND create_time&gt;=#{from}</if><if test='to != null'>AND create_time&lt;#{to}</if></where></script>
            """)
    long countSearch(@Param("operatorId") Long operatorId,@Param("targetUserId") Long targetUserId,@Param("action") String action,@Param("from") java.time.LocalDateTime from,@Param("to") java.time.LocalDateTime to);
}
