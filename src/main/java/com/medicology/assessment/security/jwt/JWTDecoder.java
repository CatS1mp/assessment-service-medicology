package com.medicology.assessment.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JWTDecoder {
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.issuer:medicology-auth}")
    private String expectedIssuer;

    @Value("${jwt.audience:medicology-api}")
    private String expectedAudience;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        String idValue = claims.get("id", String.class);
        if (idValue == null) {
            idValue = claims.get("userId", String.class);
        }
        return idValue == null ? null : UUID.fromString(idValue);
    }

    public boolean extractIsAdmin(String token) {
        Claims claims = extractAllClaims(token);
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (Boolean.TRUE.equals(isAdmin)) {
            return true;
        }

        String role = claims.get("role", String.class);
        if (role != null) {
            return role.toUpperCase(Locale.ROOT).contains("ADMIN");
        }

        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream()
                    .map(Object::toString)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .anyMatch(value -> value.contains("ADMIN"));
        }

        return false;
    }

    public boolean isTokenValid(String token) {
        return isTokenValid(token, "access");
    }

    public boolean isTokenValid(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenType = claims.get("type", String.class);
            if (!expectedType.equals(tokenType)) {
                return false;
            }
            String iss = claims.getIssuer();
            if (iss == null || !expectedIssuer.equals(iss)) {
                return false;
            }
            Object aud = claims.get("aud");
            if (aud == null) {
                return false;
            }
            if (aud instanceof String s) {
                return expectedAudience.equals(s);
            }
            if (aud instanceof Collection<?> collection) {
                return collection.stream().anyMatch(expectedAudience::equals);
            }
            return false;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
