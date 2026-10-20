package com.select.global.security.jwt;

import com.select.global.exception.ErrorCode;
import com.select.global.security.SecurityErrorResponseWriter;
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

    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorResponseWriter securityErrorResponseWriter) {
        this.jwtProvider = jwtProvider;
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            securityErrorResponseWriter.writeErrorResponse(response,ErrorCode.UNAUTHORIZED);
            return;
        }
        String token = authorization.substring(7);
        String userId;
        try {
            userId = jwtProvider.getUserId(token);
        } catch (JwtException | IllegalArgumentException e) {
            securityErrorResponseWriter.writeErrorResponse(response,ErrorCode.INVALID_TOKEN);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId,null,List.of());

        request.setAttribute("userId",userId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request,response);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return path.equals("/auth/signup")
                || path.equals("/auth/login") || method.equals("OPTIONS");
    }


}
