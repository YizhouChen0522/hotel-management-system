package com.johnny.hotel.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Data @Component @ConfigurationProperties(prefix="hotel.payment.recovery")
public class PaymentRecoveryProperties {
    private boolean enabled=true;
    private Duration fixedDelay=Duration.ofSeconds(30);
    private Duration pendingQueryAfter=Duration.ofMinutes(2);
    private int batchSize=20;
    private Duration leaseDuration=Duration.ofMinutes(1);
    private Duration initialBackoff=Duration.ofSeconds(30);
    private Duration maxBackoff=Duration.ofMinutes(30);
    public Duration backoff(int retryCount){
        long multiplier=1L<<Math.min(Math.max(0,retryCount-1),20);
        Duration value=initialBackoff.multipliedBy(multiplier);
        return value.compareTo(maxBackoff)>0?maxBackoff:value;
    }
}
