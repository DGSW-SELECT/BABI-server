package com.select.global.security.jwt;

import com.select.global.exception.ErrorCode;
import com.select.global.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


class JwtAuthenticationFilterTest {

    private ObjectMapper objectMapper;
    private JwtProvider jwtProvider;
    private SecretKey signingKey;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        objectMapper = new ObjectMapper();
        String secret = Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        jwtProvider = new JwtProvider(secret, 30);
        filter = new JwtAuthenticationFilter(jwtProvider, new SecurityErrorResponseWriter(objectMapper));
        request = new MockHttpServletRequest("GET", "/test/auth");
        request.setServletPath("/test/auth");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationReturns401WithoutContinuingChain() throws Exception {
        assertUnauthorized(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void invalidJwtReturns401WithoutContinuingChain() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");

        assertUnauthorized(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void expiredJwtReturns401WithoutContinuingChain() throws Exception {
        Instant now = Instant.now();
        String token = Jwts.builder().subject("42")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(signingKey).compact();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        assertUnauthorized(ErrorCode.INVALID_TOKEN);
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

    @Test
    void validJwtRegistersAuthenticationBeforeContinuingChain() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42"));
        FilterChain inspectingChain = (currentRequest, currentResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication);
            assertTrue(authentication.isAuthenticated());
            assertEquals("42", authentication.getPrincipal());
            assertNull(authentication.getCredentials());
            assertTrue(authentication.getAuthorities().isEmpty());
            assertEquals("42", currentRequest.getAttribute("userId"));
        };

        filter.doFilter(request, response, inspectingChain);

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void optionsRequestContinuesWithoutJwtOrAuthentication() throws Exception {
        request.setMethod("OPTIONS");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization");

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNull(request.getAttribute("userId"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
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
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic credentials", "Bearer "})
    void unsupportedOrEmptyAuthorizationReturns401(String authorization) throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);

        assertUnauthorized(authorization.equals("Bearer ") ? ErrorCode.INVALID_TOKEN : ErrorCode.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login/extra", "/auth/signup/extra"})
    void similarPathsAreStillProtected(String path) throws Exception {
        request.setRequestURI(path);
        request.setServletPath(path);

        assertUnauthorized(ErrorCode.UNAUTHORIZED);
    }

    private void assertUnauthorized(ErrorCode expectedError) throws Exception {
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals("UTF-8", response.getCharacterEncoding());
        var body = objectMapper.readTree(response.getContentAsString());
        assertFalse(body.get("success").asBoolean());
        assertEquals(expectedError.name(), body.get("error").get("code").asString());
        assertEquals(expectedError.getMessage(), body.get("message").asString());
        assertTrue(body.get("data").isNull());
        assertTrue(body.get("error").get("details").isArray());
        assertTrue(body.get("error").get("details").isEmpty());
        assertNull(request.getAttribute("userId"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }
}
