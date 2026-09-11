package com.johnny.hotel.wallet;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class WalletViews {
    private WalletViews() {}
    @Builder public record Account(Long id, Long userId, String currency, BigDecimal balance, WalletStatus status) {
        static Account from(Wallet w) {return new Account(w.getId(),w.getUserId(),w.getCurrency(),w.getBalance(),WalletStatus.fromCode(w.getStatus()));}
    }
    // Read models intentionally omit other users' actor identifiers and internal request keys.
    @Builder public record Transaction(Long id, WalletTransactionType type, BigDecimal amount, BigDecimal balanceBefore,
            BigDecimal balanceAfter, String sourceType, Long sourceId, LocalDateTime createTime) {
        static Transaction from(WalletTransaction t) {return new Transaction(t.getId(),WalletTransactionType.fromCode(t.getTransactionType()),
                t.getAmount(),t.getBalanceBefore(),t.getBalanceAfter(),t.getSourceType(),t.getSourceId(),t.getCreateTime());}
    }
    @Builder public record TopUp(Long id, Long walletId, BigDecimal amount, TopUpStatus status, String reason,
            LocalDateTime createTime, LocalDateTime resolvedTime) {
        static TopUp from(WalletTopUp t) {return new TopUp(t.getId(),t.getWalletId(),t.getAmount(),
                TopUpStatus.fromCode(t.getStatus()),t.getReason(),t.getCreateTime(),t.getResolvedTime());}
    }
}
