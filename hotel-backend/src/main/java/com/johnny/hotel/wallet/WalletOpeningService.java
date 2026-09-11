package com.johnny.hotel.wallet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.johnny.hotel.service.support.BillingRules.one;

@Service
@RequiredArgsConstructor
public class WalletOpeningService {
    private final WalletMapper wallets;
    // Must join the new user's transaction; no independently committed orphan account.
    @Transactional(propagation=Propagation.MANDATORY)
    public void openForNewUser(Long userId) {one(wallets.open(userId));}
}
