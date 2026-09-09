package com.johnny.hotel.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import java.time.Duration;

@Data @Validated @ConfigurationProperties("hotel.redis")
public class HotelRedisProperties {
    @NotNull @Pattern(regexp="[A-Za-z0-9:_-]+:") private String prefix="hotel:";
    @NotNull private Duration detailTtl=Duration.ofMinutes(30);
    @NotNull private Duration listTtl=Duration.ofMinutes(10);
    @NotNull private Duration rateTtl=Duration.ofSeconds(60);
    @NotNull private Duration authWindow=Duration.ofMinutes(1);
    @Min(1) private int loginLimit=60;
    @Min(1) private int registrationLimit=20;
    @AssertTrue(message="Redis TTLs and authentication window must be positive")
    public boolean isDurationValid(){return java.util.stream.Stream.of(detailTtl,listTtl,rateTtl,authWindow).allMatch(d->d!=null&&d.toMillis()>0);}
}
