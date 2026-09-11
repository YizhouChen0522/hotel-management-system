package com.johnny.hotel.stay;
import java.util.Arrays;
public enum StayAdjustmentType {
    EXTENSION(1), EARLY_CHECKOUT(2), LATE_CHECKOUT(3);
    private final int code;
    StayAdjustmentType(int code){this.code=code;}
    public int getCode(){return code;}
    public static StayAdjustmentType fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown stay adjustment type"));}
}
