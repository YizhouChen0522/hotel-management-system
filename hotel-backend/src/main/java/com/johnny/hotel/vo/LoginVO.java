package com.johnny.hotel.vo;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class LoginVO {
    private String token;
    private UserVO user;
}
