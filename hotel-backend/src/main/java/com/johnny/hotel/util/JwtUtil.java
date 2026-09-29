package com.johnny.hotel.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import com.johnny.hotel.security.AuthenticationSurface;

@Component
public class JwtUtil {

    private final String secretKey;
    private final long expirationTime;



    public JwtUtil(@Value("${jwt.secret}") String secretKey,
                   @Value("${jwt.expiration}") long expirationTime) {
        this.expirationTime = expirationTime;
        this.secretKey = secretKey;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(Long userId,
                                String email,
                                String username,
                                List<String> roles) {
        AuthenticationSurface surface=roles.size()==1&&roles.contains("CUSTOMER")?AuthenticationSurface.CUSTOMER_PUBLIC:AuthenticationSurface.PMS_INTERNAL;
        return generateToken(userId,email,username,roles,surface);
    }

    public String generateToken(Long userId,String email,String username,List<String> roles,AuthenticationSurface surface) {

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("username", username)
                .claim("roles", roles)
                .claim("surface", surface.name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public AuthenticationSurface getSurface(Claims claims) {
        String value=claims.get("surface",String.class);
        return value==null?null:AuthenticationSurface.valueOf(value);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    public String getUsernameFromToken(String token) {
        return parseToken(token)
                .get("username", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseToken(token)
                .get("roles", List.class);
    }
}
