package com.johnny.hotel.wallet;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    private Long id;
    private Long userId;
    private String currency;
    private BigDecimal balance;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
