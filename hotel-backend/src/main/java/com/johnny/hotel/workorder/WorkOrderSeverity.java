package com.johnny.hotel.workorder;
public enum WorkOrderSeverity { LOW(0),MEDIUM(1),HIGH(2),CRITICAL(3);private final int code;WorkOrderSeverity(int code){this.code=code;}public int getCode(){return code;}public static WorkOrderSeverity fromCode(int code){for(var v:values())if(v.code==code)return v;throw new IllegalArgumentException("Invalid severity");}}
