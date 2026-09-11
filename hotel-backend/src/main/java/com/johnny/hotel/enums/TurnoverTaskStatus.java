package com.johnny.hotel.enums;
import java.util.Arrays;
public enum TurnoverTaskStatus {
    PENDING(0), ACCEPTED(1), COMPLETED(2);
    private final int code;
    TurnoverTaskStatus(int code){this.code=code;}
    public int getCode(){return code;}
    public static TurnoverTaskStatus fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown turnover status"));}
}
