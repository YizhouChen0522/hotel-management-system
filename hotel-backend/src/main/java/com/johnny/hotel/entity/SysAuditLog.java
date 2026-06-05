package com.johnny.hotel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysAuditLog {

    private Long id;

    private Long operatorId;

    private Long targetUserId;

    private String action;

    private String detail;

    private LocalDateTime createTime;
}

