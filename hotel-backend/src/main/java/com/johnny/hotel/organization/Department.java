package com.johnny.hotel.organization;
import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Department {private Long id,managerUserId,version;private String name;private Integer status;private LocalDateTime createTime,updateTime;}
