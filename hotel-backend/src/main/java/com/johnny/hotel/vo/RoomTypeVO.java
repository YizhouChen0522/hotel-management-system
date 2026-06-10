package com.johnny.hotel.vo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeVO {
    private Long id;
    private String typeName;
    private String description;
    private BigDecimal basePrice;
    private Integer capacity;
    private Integer status; // 0: inactive, 1: active
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
