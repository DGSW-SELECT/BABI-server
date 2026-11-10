package com.select.global.security.jwt;

import com.select.global.security.SecurityErrorResponseWriter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthCookieSecurityTest {
    @Test
    void cookiesAreHttpOnlySecureAndSameSite() {
        var response = new MockHttpServletResponse();
        new AuthCookies(true, 30, new org.springframework.mock.env.MockEnvironment()).set(response, "access", "refresh");
        var cookies = response.getHeaders("Set-Cookie");
        assertEquals(3, cookies.size());
        assertTrue(cookies.get(0).contains("HttpOnly"));
        assertTrue(cookies.get(1).contains("HttpOnly"));
        assertTrue(cookies.stream().allMatch(c -> c.contains("Secure") && c.contains("SameSite=Lax")));
        assertFalse(cookies.get(2).contains("HttpOnly"));
    }

    @Test
    void cookieMutationRequiresMatchingCsrfHeader() throws Exception {
        var errors = mock(SecurityErrorResponseWriter.class);
        var filter = new CookieCsrfFilter(errors);
        var request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.setServletPath("/auth/refresh");
        request.setCookies(new Cookie(AuthCookies.REFRESH, "token"), new Cookie(AuthCookies.CSRF, "csrf"));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(errors).writeErrorResponse(eq(response), any());
        assertNull(chain.getRequest());

        String signed = CsrfToken.create("token", new byte[32]);
        var valid = new MockHttpServletRequest("POST", "/auth/refresh");
        valid.setServletPath("/auth/refresh");
        valid.setCookies(new Cookie(AuthCookies.REFRESH, "token"), new Cookie(AuthCookies.CSRF, signed));
        valid.addHeader("X-CSRF-Token", signed);
        var allowed = new MockFilterChain();
        filter.doFilter(valid, new MockHttpServletResponse(), allowed);
        assertNotNull(allowed.getRequest());
    }

    @Test
    void attackerChosenMatchingCookieAndHeaderAreRejected() throws Exception {
        var errors = mock(SecurityErrorResponseWriter.class);
        var request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.setServletPath("/auth/refresh");
        String forged = CsrfToken.create("attacker-refresh", new byte[32]);
        assertFalse(CsrfToken.valid("", forged));
        request.setCookies(new Cookie(AuthCookies.REFRESH, "victim-refresh"), new Cookie(AuthCookies.CSRF, forged));
        request.addHeader("X-CSRF-Token", forged);
        var chain = new MockFilterChain();
        new CookieCsrfFilter(errors).doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(chain.getRequest());
        verify(errors).writeErrorResponse(any(), any());
    }

    @Test
    void refreshCannotBypassCsrfWithAnArbitraryBearerHeader() throws Exception {
        var errors = mock(SecurityErrorResponseWriter.class);
        var request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.setServletPath("/auth/refresh");
        request.setCookies(new Cookie(AuthCookies.REFRESH, "token"));
        request.addHeader("Authorization", "Bearer arbitrary");
        var chain = new MockFilterChain();
        new CookieCsrfFilter(errors).doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(chain.getRequest());
        verify(errors).writeErrorResponse(any(), any());
    }

    @Test
    void insecureCookiesCannotBeEnabledForProductionOrMixedProfiles() {
        var env = new org.springframework.mock.env.MockEnvironment().withProperty("x", "y");
        env.setActiveProfiles("local", "aws");
        assertThrows(IllegalArgumentException.class, () -> new AuthCookies(false, 30, env));
    }
}
