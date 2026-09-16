package com.johnny.hotel.extras; public enum GuestPurchaseStatus {PENDING(0),CONFIRMED(1),CANCELLED(2);private final int code;GuestPurchaseStatus(int c){code=c;}public int getCode(){return code;}}
