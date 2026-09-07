package com.johnny.hotel.vo;

import com.johnny.hotel.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVO {

    private Long id;

    private Long folioId;

    private BigDecimal amount;

    private String paymentMethod;

    private String status;

    private String referenceNo;

    private LocalDateTime paidTime;

    public static PaymentVO from(Payment payment) {

        return PaymentVO.builder()
                .id(payment.getId())
                .folioId(payment.getFolioId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .referenceNo(payment.getReferenceNo())
                .paidTime(payment.getPaidTime())
                .build();
    }
}
