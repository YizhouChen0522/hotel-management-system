package com.johnny.hotel.inspection; public enum InspectionStatus { PENDING(0),PASSED(1),FAILED(2); private final int code; InspectionStatus(int c){code=c;} public int getCode(){return code;} }
