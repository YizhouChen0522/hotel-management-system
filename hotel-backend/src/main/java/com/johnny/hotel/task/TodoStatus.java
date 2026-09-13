package com.johnny.hotel.task;
import java.util.Arrays;
public enum TodoStatus { PENDING(0), IN_PROGRESS(1), COMPLETED(2), BLOCKED(3);
 private final int code; TodoStatus(int code){this.code=code;} public int getCode(){return code;}
 public static TodoStatus fromCode(int code){return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow();}
}
