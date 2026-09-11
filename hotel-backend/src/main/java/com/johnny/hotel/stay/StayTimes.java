package com.johnny.hotel.stay;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.*;
@Data @Component @ConfigurationProperties(prefix="hotel.stay")
public class StayTimes {
    private LocalTime normalCheckout=LocalTime.NOON;
    private Duration grace=Duration.ofMinutes(30);
    private LocalTime normalCheckin=LocalTime.of(15,30);
    private LocalTime lateCutoff=LocalTime.of(18,0);
    public LocalTime freeUntil(){return normalCheckout.plus(grace);}
}
