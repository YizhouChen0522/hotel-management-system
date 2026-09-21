package com.johnny.hotel.booking.deposit;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DepositSettlement {
    private Long id;
    private Long accountId;
    private Long bookingId;
    private String kind;
    private BigDecimal amount;
    private Integer status;
    private String eventKey;
    private Long createdBy;
    private Long processedBy;
    private String externalReference;
    private LocalDateTime createTime;
    private LocalDateTime processedTime;
}
