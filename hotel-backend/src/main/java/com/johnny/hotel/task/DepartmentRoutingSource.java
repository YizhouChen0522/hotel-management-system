package com.johnny.hotel.task;

public enum DepartmentRoutingSource {
 AUTO_DEPARTMENT_MANAGER(0), MANAGEMENT_OVERRIDE(1);
 private final int code;
 DepartmentRoutingSource(int code){this.code=code;}
 public int getCode(){return code;}
}
