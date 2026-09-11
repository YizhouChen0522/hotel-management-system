package com.johnny.hotel.wallet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TopUpStatus {
    PENDING(0), SUCCESS(1), REJECTED(2);
    private final int code;
    public static TopUpStatus fromCode(int code) {
        for (var value : values()) if (value.code == code) return value;
        throw new IllegalArgumentException("Unknown TopUpStatus code: " + code);
    }
}
