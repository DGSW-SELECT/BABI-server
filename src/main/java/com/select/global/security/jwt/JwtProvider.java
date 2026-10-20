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


    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String createAccessToken(String id) {
        Instant now = Instant.now();
        Instant end = now.plus(30, ChronoUnit.MINUTES);

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

        return parseJwt.getSubject();
    }
}
