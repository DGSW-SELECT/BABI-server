package com.select.global.security;

import com.select.global.exception.ErrorCode;
import com.select.global.security.jwt.JwtAuthenticationFilter;
import com.select.global.security.jwt.JwtProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityErrorResponseWriter securityErrorResponseWriter) throws Exception {
        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(
                    SecurityPaths.LOGIN,
                    SecurityPaths.SIGNUP,
                    SecurityPaths.EMAIL_SEND,
                    SecurityPaths.EMAIL_VERIFY).permitAll().anyRequest().authenticated();
        });
        http.exceptionHandling(exception -> {
            exception.authenticationEntryPoint((request, response, authException) -> {
                securityErrorResponseWriter.writeErrorResponse(
                        response,
                        ErrorCode.UNAUTHORIZED
                );
            });
            exception.accessDeniedHandler((request, response, accessDeniedException) -> {
                securityErrorResponseWriter.writeErrorResponse(
                        response,
                        ErrorCode.FORBIDDEN
                );
            });
        });
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtProvider jwtProvider,
            SecurityErrorResponseWriter securityErrorResponseWriter
    ) {
        return new JwtAuthenticationFilter(jwtProvider,securityErrorResponseWriter);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
