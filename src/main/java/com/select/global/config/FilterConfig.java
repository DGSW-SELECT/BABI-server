package com.select.global.config;

import com.select.global.security.jwt.JwtAuthenticationFilter;

import com.select.global.security.jwt.JwtProvider;

import org.springframework.boot.web.servlet.FilterRegistrationBean;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter(
            JwtProvider jwtProvider
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthenticationFilter(jwtProvider));
        return registration;
    }

}