package com.johnny.hotel.workorder;
public enum WorkOrderStatus { OPEN(0),RESOLVED(1),CANCELLED(2);private final int code;WorkOrderStatus(int code){this.code=code;}public int getCode(){return code;}public static WorkOrderStatus fromCode(int code){for(var v:values())if(v.code==code)return v;throw new IllegalArgumentException("Invalid WorkOrder status");}}
