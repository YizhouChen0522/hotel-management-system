package com.johnny.hotel.task;
import java.util.Arrays;
public enum TaskStatus { PENDING(0), IN_PROGRESS(1), COMPLETED(2), CANCELLED(3); private final int code; TaskStatus(int code){this.code=code;} public int getCode(){return code;} public static TaskStatus fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow();} }
