package com.johnny.hotel.wallet;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopUp {
    private Long id;
    private Long walletId;
    private BigDecimal amount;
    private String requestKey;
    private Integer status;
    private Long requestedBy;
    private Long resolvedBy;
    private String resolutionKey;
    private String reason;
    private LocalDateTime createTime;
    private LocalDateTime resolvedTime;
}
