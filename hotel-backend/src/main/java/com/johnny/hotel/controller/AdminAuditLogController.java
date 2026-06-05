package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {
    private final SysAuditLogMapper sysAuditLogMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<List<AuditLogVO>> getLatestLogs() {
        List<AuditLogVO> logs = sysAuditLogMapper.selectLatestLogs()
                .stream()
                .map(this::toVO)
                .toList();

        return Result.success(logs);
    }
    @GetMapping("/target")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<List<AuditLogVO>> getLogsByTargetUserId(@RequestParam Long userId) {
        List<AuditLogVO> logs = sysAuditLogMapper.selectLogsByTargetUserId(userId)
                .stream()
                .map(this::toVO)
                .toList();

        return Result.success(logs);
    }
    @GetMapping("/operator")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<List<AuditLogVO>> getLogsByOperatorId(@RequestParam Long userId) {
        List<AuditLogVO> logs = sysAuditLogMapper.selectLogsByOperatorId(userId)
                .stream()
                .map(this::toVO)
                .toList();

        return Result.success(logs);
    }
    private AuditLogVO toVO(SysAuditLog log) {
        return AuditLogVO.builder()
                .id(log.getId())
                .operatorId(log.getOperatorId())
                .targetUserId(log.getTargetUserId())
                .action(log.getAction())
                .detail(log.getDetail())
                .createTime(log.getCreateTime())
                .build();
    }
}
