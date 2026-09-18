package com.johnny.hotel.pricing;
import lombok.*;import java.math.BigDecimal;import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DynamicPolicy{private Long id;private Integer versionNo;private String name;private Integer status;private Integer activeSlot;private BigDecimal minimumMultiplier;private BigDecimal maximumMultiplier;private Long createdBy;private Long activatedBy;private LocalDateTime createTime,updateTime,activatedTime;}
