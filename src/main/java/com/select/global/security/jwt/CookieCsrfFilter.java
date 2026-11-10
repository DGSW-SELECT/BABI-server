package com.select.global.security.jwt;

import com.select.global.exception.ErrorCode;
import com.select.global.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/** Session-bound signed double-submit protection for browser cookie requests. */
public class CookieCsrfFilter extends OncePerRequestFilter {
    private final SecurityErrorResponseWriter errors;
    public CookieCsrfFilter(SecurityErrorResponseWriter errors) { this.errors = errors; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean safe = Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
        String authorization = request.getHeader("Authorization");
        boolean bearer = authorization != null && authorization.startsWith("Bearer ")
                && !request.getServletPath().equals("/auth/refresh")
                && !request.getServletPath().equals("/auth/logout");
        boolean cookieSession = AuthCookies.get(request, AuthCookies.ACCESS) != null || AuthCookies.get(request, AuthCookies.REFRESH) != null;
        if (!safe && cookieSession && !bearer && !request.getServletPath().equals("/auth/login")) {
            String cookie = AuthCookies.get(request, AuthCookies.CSRF);
            String header = request.getHeader("X-CSRF-Token");
            String refresh = AuthCookies.get(request, AuthCookies.REFRESH);
            if (cookie == null || header == null || !MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8))
                    || !CsrfToken.valid(refresh, cookie)) {
                errors.writeErrorResponse(response, ErrorCode.FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
