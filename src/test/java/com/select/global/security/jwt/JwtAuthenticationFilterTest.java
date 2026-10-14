package com.select.global.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JwtAuthenticationFilterTest {

    private JwtProvider jwtProvider;
    private SecretKey signingKey;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        jwtProvider = new JwtProvider(secret);
        filter = new JwtAuthenticationFilter(jwtProvider);
        request = new MockHttpServletRequest("GET", "/test/auth");
        request.setServletPath("/test/auth");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void missingAuthorizationReturns401WithoutContinuingChain() throws Exception {
        assertUnauthorized();
    }

    @Test
    void invalidJwtReturns401WithoutContinuingChain() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");

        assertUnauthorized();
    }

    @Test
    void expiredJwtReturns401WithoutContinuingChain() throws Exception {
        Instant now = Instant.now();
        String token = Jwts.builder().subject("42")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(signingKey).compact();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        assertUnauthorized();
    }

    @Test
    void validJwtContinuesFilterChain() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42"));

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void validJwtStoresUserIdBeforeContinuingChain() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42"));
        FilterChain inspectingChain = (currentRequest, currentResponse) ->
                assertEquals("42", currentRequest.getAttribute("userId"));

        filter.doFilter(request, response, inspectingChain);

        assertEquals("42", request.getAttribute("userId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth/signup"})
    void publicAuthEndpointsContinueWithoutJwt(String path) throws Exception {
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setServletPath(path);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNull(request.getAttribute("userId"));
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic credentials", "Bearer "})
    void unsupportedOrEmptyAuthorizationReturns401(String authorization) throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);

        assertUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login/extra", "/auth/signup/extra"})
    void similarPathsAreStillProtected(String path) throws Exception {
        request.setRequestURI(path);
        request.setServletPath(path);

        assertUnauthorized();
    }

    private void assertUnauthorized() throws Exception {
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(request.getAttribute("userId"));
        verifyNoInteractions(chain);
    }
}
