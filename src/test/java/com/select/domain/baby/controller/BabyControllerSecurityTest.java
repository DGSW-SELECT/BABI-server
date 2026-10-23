package com.select.domain.baby.controller;

import com.select.domain.baby.service.BabyService;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.response.BabyMemberResponse;
import com.select.global.exception.GlobalExceptionHandler;
import com.select.global.security.SecurityConfig;
import com.select.global.security.SecurityErrorResponseWriter;
import com.select.global.security.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BabyControllerSecurityTest {

    private AnnotationConfigWebApplicationContext context;
    private BabyService babyService;
    private JwtProvider jwtProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityTestConfig.class);
        context.refresh();
        babyService = context.getBean(BabyService.class);
        jwtProvider = context.getBean(JwtProvider.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void getBabiesWithoutAuthenticationReturnsUnauthorizedBeforeCallingService() throws Exception {
        mockMvc.perform(get("/babies").servletPath("/babies"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.details").isEmpty());

        verifyNoInteractions(babyService, jwtProvider);
    }

    @Test
    void getBabiesWithInvalidTokenReturnsUnauthorizedBeforeCallingService() throws Exception {
        when(jwtProvider.getUserId("invalid-token")).thenThrow(new JwtException("invalid token"));

        mockMvc.perform(get("/babies").servletPath("/babies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        verify(jwtProvider).getUserId("invalid-token");
        verifyNoInteractions(babyService);
    }

    @Test
    void getBabiesWithValidTokenReachesControllerUsingJwtUserId() throws Exception {
        when(jwtProvider.getUserId("valid-token")).thenReturn("7");
        when(babyService.getBabies(7L)).thenReturn(List.of());

        mockMvc.perform(get("/babies").servletPath("/babies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(jwtProvider).getUserId("valid-token");
        verify(babyService).getBabies(7L);
    }

    @Test
    void getBabyMembersWithoutJwtReturnsUnauthorizedBeforeCallingService() throws Exception {
        mockMvc.perform(get("/babies/101/members").servletPath("/babies/101/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(babyService, jwtProvider);
    }

    @Test
    void getBabyMembersWithInvalidJwtReturnsUnauthorizedBeforeCallingService() throws Exception {
        when(jwtProvider.getUserId("invalid-token")).thenThrow(new JwtException("invalid token"));

        mockMvc.perform(get("/babies/101/members").servletPath("/babies/101/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        verify(jwtProvider).getUserId("invalid-token");
        verifyNoInteractions(babyService);
    }

    @Test
    void getBabyMembersWithValidJwtPassesAuthenticatedUserIdToService() throws Exception {
        when(jwtProvider.getUserId("valid-token")).thenReturn("7");
        when(babyService.getBabyMembers(101L, 7L)).thenReturn(List.of(
                new BabyMemberResponse(7L, "보호자1", BabyRole.OWNER)
        ));

        mockMvc.perform(get("/babies/101/members").servletPath("/babies/101/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(7))
                .andExpect(jsonPath("$.data[0].nickname").value("보호자1"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));

        verify(jwtProvider).getUserId("valid-token");
        verify(babyService).getBabyMembers(101L, 7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, BabyController.class, GlobalExceptionHandler.class})
    static class SecurityTestConfig {

        @Bean
        BabyService babyService() {
            return mock(BabyService.class);
        }

        @Bean
        JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }

        @Bean
        SecurityErrorResponseWriter securityErrorResponseWriter() {
            return new SecurityErrorResponseWriter(new ObjectMapper());
        }
    }
}
