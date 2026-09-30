package com.johnny.hotel.config;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecuritySurfaceProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final SecuritySurfaceProperties surfaceProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,SecuritySurfaceProperties surfaceProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.surfaceProperties=surfaceProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors->cors.configurationSource(corsConfigurationSource()))

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register/customer",
                                "/api/internal/auth/login",
                                "/api/public/auth/customer/login",
                                "/api/public/auth/customer/register",
                                "/api/public/catalog/**",
                                "/api/public/site/**",
                                "/api/health",
                                "/api/auth/register/employee",
                                "/api/payments/webhooks/**"
                        ).permitAll()
                        .requestMatchers("/api/public/customer/**").access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(authentication.get().getAuthorities().stream().anyMatch(a->"SURFACE_CUSTOMER_PUBLIC".equals(a.getAuthority()))&&authentication.get().getAuthorities().stream().anyMatch(a->"ROLE_CUSTOMER".equals(a.getAuthority()))))
                        .requestMatchers("/api/public/**").denyAll()
                        .anyRequest().hasAuthority("SURFACE_PMS_INTERNAL")
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");

                            Result<Void> result = Result.error(401, "Unauthorized");
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");

                            Result<Void> result = Result.error(403, "Forbidden");
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean CorsConfigurationSource corsConfigurationSource(){var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/public/**",cors(surfaceProperties.getPublicSurface().getAllowedOrigins()));source.registerCorsConfiguration("/api/**",cors(surfaceProperties.getInternal().getAllowedOrigins()));return source;}
    private CorsConfiguration cors(List<String> origins){var c=new CorsConfiguration();c.setAllowedOrigins(origins==null?List.of():origins.stream().filter(s->s!=null&&!s.isBlank()).toList());c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key"));c.setAllowCredentials(true);c.setMaxAge(3600L);return c;}

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
