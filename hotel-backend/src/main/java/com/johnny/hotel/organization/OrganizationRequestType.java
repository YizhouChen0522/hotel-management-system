package com.johnny.hotel.organization;
public enum OrganizationRequestType { CREATE_DEPARTMENT(0), DISABLE_DEPARTMENT(1), CHANGE_DEPARTMENT_MANAGER(2), REMOVE_EMPLOYEE_FROM_DEPARTMENT(3), MOVE_EMPLOYEE_TO_DEPARTMENT(4);
 private final int code;OrganizationRequestType(int code){this.code=code;}public int getCode(){return code;}
 public static OrganizationRequestType fromCode(int code){for(var v:values())if(v.code==code)return v;throw new IllegalArgumentException("Invalid code");}
}
