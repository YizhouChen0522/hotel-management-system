package com.johnny.hotel.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum UserStatus {

    INACTIVE(0),
    ACTIVE(1),
    PENDING(2),
    REJECTED(3);

    private final int code;

    public static UserStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown UserStatus code: " + code
                        )
                );
    }
}
