package com.johnny.hotel.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum RefundStatus {

    PENDING(0),
    SUCCESS(1),
    FAILED(2);

    private final int code;

    public static RefundStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown RefundStatus code: " + code
                        )
                );
    }
}