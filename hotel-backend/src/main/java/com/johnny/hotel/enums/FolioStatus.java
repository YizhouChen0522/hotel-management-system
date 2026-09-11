package com.johnny.hotel.enums;

import java.math.BigDecimal;
import java.util.Arrays;

public enum FolioStatus {
    PENDING(0), COMPLETED(1), REFUND_PENDING(2);
    private final int code;
    FolioStatus(int code) { this.code=code; }
    public int getCode() { return code; }
    public static FolioStatus fromCode(int code) {
        return Arrays.stream(values()).filter(v->v.code==code).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown Folio status"));
    }
    public static FolioStatus forBalance(BigDecimal balance) {
        return balance.signum()>0?PENDING:balance.signum()<0?REFUND_PENDING:COMPLETED;
    }
}
