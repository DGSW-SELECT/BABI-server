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
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private String secret;
    private JwtProvider jwtProvider;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        secret = Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        jwtProvider = new JwtProvider(secret, 30);
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

    @ParameterizedTest
    @ValueSource(longs = {1, 7, 60})
    void configuredExpirationMinutesAreApplied(long expirationMinutes) {
        JwtProvider provider = new JwtProvider(secret, expirationMinutes);
        String token = provider.createAccessToken("42");
        var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

        assertEquals(Duration.ofMinutes(expirationMinutes), Duration.between(
                claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()
        ));
        assertEquals("42", provider.getUserId(token));
    }

    @Test
    void springInjectsNonDefaultExpirationProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtProvider.class)
                .withPropertyValues("jwt.secret=" + secret, "jwt.access-token-expiration-minutes=7")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    JwtProvider provider = context.getBean(JwtProvider.class);
                    String token = provider.createAccessToken("42");
                    var claims = Jwts.parser().verifyWith(signingKey).build()
                            .parseSignedClaims(token).getPayload();

                    assertEquals(Duration.ofMinutes(7), Duration.between(
                            claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()
                    ));
                    assertEquals("42", provider.getUserId(token));
                });
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void nonPositiveExpirationIsRejectedByConstructor(long expirationMinutes) {
        assertThrows(IllegalArgumentException.class, () -> new JwtProvider(secret, expirationMinutes));
    }

    @ParameterizedTest
    @CsvSource({
            "default, , 30", "default, 7, 7",
            "local, , 30", "local, 7, 7",
            "test, , 30", "test, 7, 7"
    })
    void applicationYamlResolvesDefaultAndEnvironmentOverride(
            String profile, String environmentMinutes, long expectedMinutes
    ) {
        Map<String, Object> environmentVariables = environmentMinutes == null
                ? Map.of()
                : Map.of("JWT_ACCESS_TOKEN_EXPIRATION_MINUTES", environmentMinutes);

        configurationContext(profile, environmentVariables).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(expectedMinutes, context.getEnvironment()
                    .getProperty("jwt.access-token-expiration-minutes", Long.class));
            assertTrue(context.getEnvironment().getPropertySources().stream()
                    .anyMatch(source -> source.getName().contains("application.yml")));
            if (!profile.equals("default")) {
                assertTrue(context.getEnvironment().getPropertySources().stream()
                        .anyMatch(source -> source.getName().contains("application-" + profile + ".yml")));
            }

            JwtProvider provider = context.getBean(JwtProvider.class);
            String token = provider.createAccessToken("42");
            var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            assertEquals(Duration.ofMinutes(expectedMinutes), Duration.between(
                    claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()
            ));
            assertEquals("42", provider.getUserId(token));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nonPositiveEnvironmentExpirationPreventsBeanInitialization(String expirationMinutes) {
        configurationContext("default", Map.of("JWT_ACCESS_TOKEN_EXPIRATION_MINUTES", expirationMinutes))
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    while (failure.getCause() != null) {
                        failure = failure.getCause();
                    }
                    assertInstanceOf(IllegalArgumentException.class, failure);
                    assertEquals("JWT 만료 시간은 0보다 커야 합니다.", failure.getMessage());
                });
    }

    private ApplicationContextRunner configurationContext(String profile, Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
                .withUserConfiguration(JwtProvider.class)
                .withPropertyValues("jwt.secret=" + secret)
                .withInitializer(context -> {
                    var environment = context.getEnvironment();
                    // Isolate only this context from real process secrets, profiles, and environment settings.
                    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables
                    ));
                    environment.setActiveProfiles(profile.equals("default") ? new String[0] : new String[] {profile});
                    new ConfigDataApplicationContextInitializer().initialize(context);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "9223372036854775807"})
    void getUserIdReturnsValidPositiveUserId(String userId) {
        String token = jwtProvider.createAccessToken(userId);

        assertEquals(userId, jwtProvider.getUserId(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "1e2", "9223372036854775808"})
    void correctlySignedTokenWithNonNumericOrOverflowingUserIdIsRejected(String userId) {
        String token = jwtProvider.createAccessToken(userId);

        assertThrows(JwtException.class, () -> jwtProvider.getUserId(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-9223372036854775808"})
    void correctlySignedTokenWithNonPositiveUserIdIsRejected(String userId) {
        String token = jwtProvider.createAccessToken(userId);

        assertThrows(JwtException.class, () -> jwtProvider.getUserId(token));
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
        JwtProvider otherProvider = new JwtProvider(otherSecret, 30);
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
