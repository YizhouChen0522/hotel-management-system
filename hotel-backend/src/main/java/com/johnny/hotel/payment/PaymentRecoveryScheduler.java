package com.johnny.hotel.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PaymentRecoveryScheduler {
    private final PaymentRecoveryService recovery;
    @Scheduled(fixedDelayString="${hotel.payment.recovery.fixed-delay:30s}",initialDelayString="${hotel.payment.recovery.fixed-delay:30s}")
    public void recover(){recovery.runOneCycle();}
}
