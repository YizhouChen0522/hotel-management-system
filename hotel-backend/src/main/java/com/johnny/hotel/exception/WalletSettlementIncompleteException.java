package com.johnny.hotel.exception;

/** Deliberate partial success: only thrown after a valid, audited wallet contribution, before stay mutations. */
public class WalletSettlementIncompleteException extends BusinessException {
    public WalletSettlementIncompleteException(){super(409,"Wallet contribution retained; pay the remaining Folio balance before checkout");}
}
