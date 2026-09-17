package com.johnny.hotel.stay;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter @RequiredArgsConstructor
public enum StayStatus {
    IN_HOUSE(1), CHECKED_OUT(2);
    private final int code;
    public static StayStatus fromCode(int code) {
        for(var status:values()) if(status.code==code) return status;
        throw new IllegalArgumentException("Unknown StayStatus code: "+code);
    }
}
