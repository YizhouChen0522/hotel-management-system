package com.johnny.hotel.organization;
public enum OrganizationRequestStatus { PENDING_APPROVAL(0), APPROVED(1), REJECTED(2), CANCELLED(3), INVALIDATED(4);
 private final int code;OrganizationRequestStatus(int code){this.code=code;}public int getCode(){return code;}
 public static OrganizationRequestStatus fromCode(int code){for(var v:values())if(v.code==code)return v;throw new IllegalArgumentException("Invalid code");}
}
