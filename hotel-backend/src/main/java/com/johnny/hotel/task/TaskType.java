package com.johnny.hotel.task;
import java.util.Arrays;
public enum TaskType { ROOM_TURNOVER(0), GENERAL(1); private final int code; TaskType(int code){this.code=code;} public int getCode(){return code;} public static TaskType fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow();} }
