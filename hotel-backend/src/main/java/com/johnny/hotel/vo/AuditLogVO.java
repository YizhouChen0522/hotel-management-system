package com.johnny.hotel.vo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogVO {
    private Long id;
    private Long operatorId;
    private Long targetUserId;
    private String action;
    private String detail;
    private LocalDateTime createTime;
}
