package com.johnny.hotel.wallet;
import com.johnny.hotel.enums.RefundStatus;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Builder
public record RefundVO(Long id,Long folioId,BigDecimal amount,String currency,RefundStatus status,
                       String destination,String reason,String processReason,LocalDateTime createTime,LocalDateTime processedTime) {
    static RefundVO from(Refund r) {return new RefundVO(r.getId(),r.getFolioId(),r.getAmount(),r.getCurrency(),RefundStatus.fromCode(r.getStatus()),
            r.getDestination(),r.getReason(),r.getProcessReason(),r.getCreateTime(),r.getProcessedTime());}
}
