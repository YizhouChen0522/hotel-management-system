package com.johnny.hotel.task;
import java.util.Arrays;
public enum TaskRecordType { TASK_CREATED(0),TASK_CLAIMED(1),TASK_ASSIGNED(2),TASK_ACCEPTED(3),TASK_COMPLETED(4),TASK_FORCE_COMPLETED(5),TASK_REASSIGNED(6),TASK_CANCELLED(7); private final int code; TaskRecordType(int code){this.code=code;} public int getCode(){return code;} public static TaskRecordType fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow();} }
