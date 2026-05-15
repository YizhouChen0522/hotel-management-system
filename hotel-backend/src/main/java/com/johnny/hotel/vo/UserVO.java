package com.johnny.hotel.vo;

import lombok.Data;
import lombok.Builder;

@Builder
@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
}
