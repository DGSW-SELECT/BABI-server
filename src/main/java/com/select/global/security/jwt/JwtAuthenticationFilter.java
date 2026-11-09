package com.select.global.security.jwt;

import com.select.global.exception.ErrorCode;
import com.select.global.security.SecurityErrorResponseWriter;
import com.select.global.security.SecurityPaths;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;
    private final TokenRevocationService revocations;
    private final com.select.domain.user.repository.UserRepository users;
    private final RefreshSessionService sessions;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorResponseWriter securityErrorResponseWriter, TokenRevocationService revocations, com.select.domain.user.repository.UserRepository users, RefreshSessionService sessions) {
        this.jwtProvider = jwtProvider;
        this.securityErrorResponseWriter = securityErrorResponseWriter;
        this.revocations = revocations;
        this.users = users;
        this.sessions = sessions;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : AuthCookies.get(request, AuthCookies.ACCESS);
        if (token == null) {
            securityErrorResponseWriter.writeErrorResponse(response,ErrorCode.UNAUTHORIZED);
            return;
        }
        String userId;
        try {
            userId = jwtProvider.getUserId(token);
        } catch (JwtException | IllegalArgumentException e) {
            securityErrorResponseWriter.writeErrorResponse(response,ErrorCode.INVALID_TOKEN);
            return;
        }

        try {
            String sessionId = jwtProvider.getSessionId(token);
            if (revocations.isRevoked(token) || (sessionId != null && !sessions.isActive(sessionId)) || users.findById(Long.valueOf(userId)).filter(com.select.domain.user.entity.User::isActive).isEmpty()) {
                securityErrorResponseWriter.writeErrorResponse(response, ErrorCode.INVALID_TOKEN);
                return;
            }
        } catch (org.springframework.dao.DataAccessException e) {
            securityErrorResponseWriter.writeErrorResponse(response, ErrorCode.AUTH_SERVICE_UNAVAILABLE);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId,null,List.of());

        request.setAttribute("userId",userId);
        request.setAttribute("accessToken", token);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request,response);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return SecurityPaths.isDocumentationPath(path)
                || path.equals(SecurityPaths.SIGNUP)
                || path.equals(SecurityPaths.LOGIN)
                || path.equals("/auth/refresh")
                || path.equals("/auth/logout")
                || path.equals("/auth/csrf")
                || path.equals(SecurityPaths.EMAIL_SEND)
                || path.equals(SecurityPaths.EMAIL_VERIFY)
                || "OPTIONS".equals(request.getMethod());
    }


}
