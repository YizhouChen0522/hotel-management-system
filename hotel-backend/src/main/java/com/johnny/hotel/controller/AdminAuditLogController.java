package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.pagination.PageResult;
import com.johnny.hotel.pagination.PaginationSupport;
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
    private final PaginationSupport pagination;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<AuditLogVO>> getLatestLogs(@RequestParam(required=false) Long operatorId,@RequestParam(required=false) Long targetUserId,@RequestParam(required=false) String action,@RequestParam(required=false) java.time.LocalDateTime from,@RequestParam(required=false) java.time.LocalDateTime to,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
        return Result.success(page(operatorId,targetUserId,action,from,to,page,pageSize));
    }
    @GetMapping("/target")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<AuditLogVO>> getLogsByTargetUserId(@RequestParam Long userId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
        return Result.success(page(null,userId,null,null,null,page,pageSize));
    }
    @GetMapping("/operator")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<AuditLogVO>> getLogsByOperatorId(@RequestParam Long userId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
        return Result.success(page(userId,null,null,null,null,page,pageSize));
    }
    private PageResult<AuditLogVO> page(Long operator,Long target,String action,java.time.LocalDateTime from,java.time.LocalDateTime to,Integer page,Integer size){if(from!=null&&to!=null&&!from.isBefore(to))throw new com.johnny.hotel.exception.BusinessException("Invalid date range");String a=action==null||action.isBlank()?null:action.trim();boolean search=operator!=null||target!=null||a!=null||from!=null||to!=null;var w=pagination.window(page,size,search);int limit=pagination.limit(w);var rows=limit==0?List.<AuditLogVO>of():sysAuditLogMapper.searchPage(operator,target,a,from,to,w.offset(),limit).stream().map(this::toVO).toList();return pagination.result(w,rows,sysAuditLogMapper.countSearch(operator,target,a,from,to));}
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
