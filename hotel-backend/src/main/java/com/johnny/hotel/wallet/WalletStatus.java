package com.johnny.hotel.wallet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletStatus {
    BLOCKED(0), ACTIVE(1);
    private final int code;
    public static WalletStatus fromCode(int code) {
        for (var value : values()) if (value.code == code) return value;
        throw new IllegalArgumentException("Unknown WalletStatus code: " + code);
    }
}
