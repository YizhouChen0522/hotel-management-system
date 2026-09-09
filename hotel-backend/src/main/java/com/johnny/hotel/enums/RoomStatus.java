package com.johnny.hotel.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum RoomStatus {

    DISABLED(0),
    AVAILABLE(1),
    BOOKED(2),
    MAINTENANCE(3),
    OCCUPIED(4);

    private final int code;

    public static RoomStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown RoomStatus code: " + code
                        )
                );
    }
}
