package com.select.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMinutes;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-minutes}") long accessTokenExpirationMinutes
    ) {
        if (accessTokenExpirationMinutes <= 0) {
            throw new IllegalArgumentException("JWT 만료 시간은 0보다 커야 합니다.");
        }
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;

    }

    public String createAccessToken(String id) {
        Instant now = Instant.now();
        Instant end = now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(id)
                .issuedAt(Date.from(now))
                .expiration(Date.from(end))
                .signWith(secretKey)
                .compact();
    }

    public String getUserId(String token) {
        Claims parseJwt = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (parseJwt.getSubject() == null || parseJwt.getSubject().isBlank() || parseJwt.getExpiration() == null) {
            throw new JwtException("유저 아이디를 찾지 못했습니다.");
        }

        try {
            Long id = Long.parseLong(parseJwt.getSubject());
            if (id <= 0) {
                throw new JwtException("올바르지 않은 사용자 ID입니다.");
            }
        } catch (NumberFormatException e) {
            throw new JwtException("사용자 ID가 올바른 숫자 형식이 아닙니다.",e);
        }

        return parseJwt.getSubject();
    }
}
