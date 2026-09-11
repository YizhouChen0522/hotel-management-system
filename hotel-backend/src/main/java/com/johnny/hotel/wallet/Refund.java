package com.johnny.hotel.wallet;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Refund {
    private Long id;
    private Long folioId;
    private Long bookingId;
    private Long userId;
    private Long walletId;
    private String currency;
    private BigDecimal amount;
    private Integer status;
    private String requestKey;
    private String reason;
    private String destination;
    private Long requestedBy;
    private Long processedBy;
    private String processKey;
    private String processReason;
    private LocalDateTime createTime;
    private LocalDateTime processedTime;
}
