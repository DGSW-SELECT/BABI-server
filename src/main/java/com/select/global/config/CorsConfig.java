package com.select.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${auth.cors.allowed-origins:}") String configured,
                                                     org.springframework.core.env.Environment environment) {
        var config = new CorsConfiguration();
        var origins = Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        var profiles = List.of(environment.getActiveProfiles());
        if (origins.isEmpty() && !profiles.isEmpty() && profiles.stream().allMatch(p -> p.equals("local") || p.equals("test"))) {
            origins = List.of("http://localhost:5173");
        }
        for (String origin : origins) {
            URI uri;
            try { uri = URI.create(origin); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("잘못된 CORS 출처입니다."); }
            if ((!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                throw new IllegalArgumentException("CORS 출처는 정확한 HTTP(S) origin이어야 합니다.");
            }
        }
        config.setAllowedOrigins(origins);
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
