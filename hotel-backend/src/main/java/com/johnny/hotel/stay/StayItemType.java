package com.johnny.hotel.stay;
/** Stable ledger names. Retention is reserved and deliberately rejected until its policy is implemented. */
public enum StayItemType {
    EARLY_CHECKOUT_REVERSAL, LATE_CHECKOUT_FEE, LATE_CHECKOUT_CONFLICT_FEE, STAY_FEE_REVERSAL,
    EARLY_DEPARTURE_RETENTION_FEE
}
