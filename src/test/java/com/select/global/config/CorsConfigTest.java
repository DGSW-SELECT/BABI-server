package com.select.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    private CorsFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        String configured = Files.readString(Path.of("deploy/app.env.example")).lines()
                .filter(line -> line.startsWith("AUTH_CORS_ALLOWED_ORIGINS="))
                .map(line -> line.substring(line.indexOf('=') + 1)).findFirst().orElseThrow();
        var environment = new MockEnvironment();
        environment.setActiveProfiles("aws");
        filter = new CorsFilter(new CorsConfig().corsConfigurationSource(configured, environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://babi.prot.kr", "https://devbabi.prot.kr"})
    void allowsConfiguredFrontendPreflight(String origin) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(preflight(origin), response, (request, result) -> fail("Preflight reached application"));

        assertEquals(200, response.getStatus());
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
        String headers = response.getHeader("Access-Control-Allow-Headers").toLowerCase(Locale.ROOT);
        assertTrue(headers.contains("authorization"));
        assertTrue(headers.contains("content-type"));
        assertTrue(headers.contains("x-csrf-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "https://babi.prot.kr.untrusted.example", "null", "http://babi.prot.kr"})
    void rejectsUntrustedFrontendPreflight(String origin) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(preflight(origin), response, (request, result) -> fail("Rejected preflight reached application"));

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://babi.prot.kr", "https://devbabi.prot.kr"})
    void preservesProtectedResponseForAllowedOrigin(String origin) throws Exception {
        var request = new MockHttpServletRequest("GET", "/babies");
        request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> response.setStatus(401));

        assertEquals(401, response.getStatus());
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
    }

    private MockHttpServletRequest preflight(String origin) {
        var request = new MockHttpServletRequest("OPTIONS", "/babies/1/daily-reports/2026-11-17");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "PATCH");
        request.addHeader("Access-Control-Request-Headers", "Authorization,Content-Type,X-CSRF-Token");
        return request;
    }
}
