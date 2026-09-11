package com.johnny.hotel.wallet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletTransactionType {
    TOP_UP(1), REFUND_CREDIT(2), FOLIO_PAYMENT(3), EMPLOYEE_BENEFIT(4);
    private final int code;
    public static WalletTransactionType fromCode(int code) {
        for (var value : values()) if (value.code == code) return value;
        throw new IllegalArgumentException("Unknown WalletTransactionType code: " + code);
    }
}
