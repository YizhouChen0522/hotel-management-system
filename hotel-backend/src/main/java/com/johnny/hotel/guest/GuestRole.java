package com.johnny.hotel.guest;
import com.johnny.hotel.exception.BusinessException;
import lombok.Getter;import lombok.RequiredArgsConstructor;
@Getter @RequiredArgsConstructor public enum GuestRole { PRIMARY(0), ACCOMPANYING(1); private final int code; public static GuestRole fromCode(Integer code){for(var v:values())if(v.code==code)return v;throw new BusinessException("Invalid guest role");}}
