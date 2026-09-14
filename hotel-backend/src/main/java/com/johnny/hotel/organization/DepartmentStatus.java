package com.johnny.hotel.organization;
public enum DepartmentStatus { DISABLED(0), ACTIVE(1);
 private final int code;DepartmentStatus(int code){this.code=code;}public int getCode(){return code;}
 public static DepartmentStatus fromCode(int code){for(var v:values())if(v.code==code)return v;throw new IllegalArgumentException("Invalid code");}
}
