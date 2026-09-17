package com.johnny.hotel.wallet;

import com.johnny.hotel.booking.deposit.DepositRefund;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.johnny.hotel.service.support.BillingRules.*;

/** Deposit account is locked before Wallet; BLOCKED only restricts top-up, never refunds. */
@Service @RequiredArgsConstructor
public class DepositRefundPosting {
    private final WalletMapper wallets;
    private final WalletPostingService posting;
    private final RefundAccess access;
    @Transactional(propagation=Propagation.MANDATORY)
    public void credit(DepositRefund source,Long actor) {
        access.approve(actor,source.getUserId());
        var wallet=wallets.lock(source.getWalletId());
        require(wallet!=null,"Deposit refund beneficiary wallet is missing");
        posting.creditDepositRefund(wallet,source,actor);
    }
}
