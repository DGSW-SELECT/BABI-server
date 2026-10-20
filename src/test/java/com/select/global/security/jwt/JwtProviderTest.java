package com.select.global.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        jwtProvider = new JwtProvider(secret);
    }

    @Test
    void createdTokenIsValidAndExpiresAfterThirtyMinutes() {
        String token = jwtProvider.createAccessToken("42");

        assertEquals("42", jwtProvider.getUserId(token));
        var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertEquals(Duration.ofMinutes(30), Duration.between(
                claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()
        ));
    }

    @Test
    void getUserIdReturnsTokenSubject() {
        String token = jwtProvider.createAccessToken("42");

        assertEquals("42", jwtProvider.getUserId(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-jwt", "invalid.jwt.value"})
    void invalidTokenIsRejected(String token) {
        assertThrows(JwtException.class, () -> jwtProvider.getUserId(token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyTokenIsRejected(String token) {
        assertThrows(IllegalArgumentException.class, () -> jwtProvider.getUserId(token));
    }

    @Test
    void tokenSignedByDifferentProviderIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());
        JwtProvider otherProvider = new JwtProvider(otherSecret);
        String token = jwtProvider.createAccessToken("42");

        assertThrows(SignatureException.class, () -> otherProvider.getUserId(token));
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = jwtProvider.createAccessToken("42");
        String[] parts = token.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        parts[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        String tamperedToken = String.join(".", parts);

        assertThrows(SignatureException.class, () -> jwtProvider.getUserId(tamperedToken));
    }

    @Test
    void expiredTokenSignedWithCorrectKeyIsRejected() {
        Instant now = Instant.now();
        String token = Jwts.builder().subject("42")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(signingKey).compact();

        assertThrows(ExpiredJwtException.class, () -> jwtProvider.getUserId(token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingOrBlankSubjectIsRejected(String subject) {
        String token = Jwts.builder().subject(subject)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(signingKey).compact();

        assertThrows(JwtException.class, () -> jwtProvider.getUserId(token));
    }

    @Test
    void missingExpirationIsRejected() {
        String token = Jwts.builder().subject("42").signWith(signingKey).compact();

        assertThrows(JwtException.class, () -> jwtProvider.getUserId(token));
    }
}
