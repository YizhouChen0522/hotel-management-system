package com.johnny.hotel.config;
import com.johnny.hotel.security.AuthRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration @RequiredArgsConstructor
public class AuthRateLimitConfig implements WebMvcConfigurer {
    private final AuthRateLimitInterceptor limiter;
    @Override public void addInterceptors(InterceptorRegistry registry){
        // Only the socket peer is used. Arbitrary X-Forwarded-For is intentionally ignored.
        registry.addInterceptor(limiter).addPathPatterns("/api/auth/login","/api/auth/register/customer","/api/auth/register/employee");
    }
}
