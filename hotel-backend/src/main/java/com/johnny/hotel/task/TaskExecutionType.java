package com.johnny.hotel.task;
public enum TaskExecutionType { PERSONAL(0), SHARED(1), GROUP_INDIVIDUAL(2), GROUP_GROUP(3);
 private final int code; TaskExecutionType(int code){this.code=code;} public int getCode(){return code;}
 public static TaskExecutionType fromCode(int code){for(var v:values())if(v.code==code)return v;throw new IllegalArgumentException("Invalid execution type");}
}
