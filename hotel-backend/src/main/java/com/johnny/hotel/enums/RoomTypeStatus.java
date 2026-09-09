package com.johnny.hotel.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum RoomTypeStatus {

    DISABLED(0),
    ENABLED(1);

    private final int code;

    public static RoomTypeStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown RoomTypeStatus code: " + code
                        )
                );
    }
}
