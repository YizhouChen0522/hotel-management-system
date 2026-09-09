package com.johnny.hotel.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum BookingStatus {

    PENDING(0),
    APPROVED(1),
    CHECKED_IN(2),
    CHECKED_OUT(3),
    CANCELLED_BY_USER(4),
    REJECTED_BY_STAFF(5);

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
