package com.johnny.hotel.task;
import java.util.Arrays;
public enum TaskAssignmentStatus { ASSIGNED(0), ACCEPTED(1), COMPLETED(2); private final int code; TaskAssignmentStatus(int code){this.code=code;} public int getCode(){return code;} public static TaskAssignmentStatus fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow();} }
