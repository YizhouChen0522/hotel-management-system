package com.johnny.hotel.wallet;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {
    private Long id;
    private Long walletId;
    private Integer transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String requestKey;
    private String sourceType;
    private Long sourceId;
    private Long operatorUserId;
    private LocalDateTime createTime;
}
