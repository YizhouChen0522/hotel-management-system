package com.johnny.hotel.config;
import lombok.Data;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.validation.annotation.Validated;import java.util.*;
@Data @Validated @ConfigurationProperties(prefix="hotel.security") public class SecuritySurfaceProperties {private Surface publicSurface=new Surface();private Surface internal=new Surface();@Data public static class Surface{private List<String> allowedOrigins=new ArrayList<>();}}
