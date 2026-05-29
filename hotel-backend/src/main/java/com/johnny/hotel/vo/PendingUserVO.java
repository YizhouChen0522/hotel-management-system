package com.johnny.hotel.vo;


import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@Builder
public class PendingUserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String applyRoleCode;
    private String applyReason;
    private Integer status;
    private LocalDateTime createTime;
}
