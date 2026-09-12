package com.johnny.hotel.task;
import java.util.Arrays;
public enum TaskAssignmentMode { CLAIMABLE(0), ASSIGNED(1), MULTI_ASSIGNED(2); private final int code; TaskAssignmentMode(int code){this.code=code;} public int getCode(){return code;} public static TaskAssignmentMode fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow();} }
