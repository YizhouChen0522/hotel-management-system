package com.johnny.hotel.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private Integer status; // 0: inactive, 1: active, 2: pending approval, 3: rejected
    private String applyRoleCode;
    private String applyReason;
    private Long approvedBy;
    private LocalDateTime approvedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
