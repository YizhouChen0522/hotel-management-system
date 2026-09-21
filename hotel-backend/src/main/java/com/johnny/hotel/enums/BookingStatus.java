package com.johnny.hotel.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum BookingStatus {

    PENDING(0),
    APPROVED(1),
    CANCELLED(4),
    REJECTED_BY_STAFF(5),
    NO_SHOW(6);

    private final int code;

    public static BookingStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown BookingStatus code: " + code
                        )
                );
    }
}
