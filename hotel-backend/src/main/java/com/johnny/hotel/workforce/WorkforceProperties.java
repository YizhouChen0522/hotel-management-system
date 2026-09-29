package com.johnny.hotel.workforce;
import jakarta.validation.constraints.Min;import lombok.Data;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.stereotype.Component;import org.springframework.validation.annotation.Validated;
@Data @Validated @Component @ConfigurationProperties(prefix="hotel.workforce") public class WorkforceProperties {@Min(1) private int missingCheckoutHours=24;}
