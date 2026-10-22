package com.select.domain.baby.controller;

import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyReadResponse;
import com.select.domain.baby.service.BabyService;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BabyControllerTest {

    private static final Long BABY_ID = 101L;
    private static final Long USER_ID = 7L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 15);

    private BabyService babyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        babyService = mock(BabyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BabyController(babyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                USER_ID.toString(), null, List.of()
        ));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBabyReturnsBabyIdAndPassesAuthenticatedUserIdToService() throws Exception {
        BabyCreateRequest request = new BabyCreateRequest("아기", BIRTH_DATE);
        when(babyService.createBaby(request, USER_ID))
                .thenReturn(new BabyCreateResponse(BABY_ID, request.name(), BIRTH_DATE));

        mockMvc.perform(post("/babies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"아기","birthDate":"2025-01-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.babyId").value(BABY_ID.intValue()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.name").value("아기"))
                .andExpect(jsonPath("$.data.birthDate").value("2025-01-15"));

        verify(babyService).createBaby(request, USER_ID);
    }

    @Test
    void getBabyReturnsBabyAndPassesAuthenticatedUserIdToService() throws Exception {
        when(babyService.getBaby(BABY_ID, USER_ID))
                .thenReturn(new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE));

        mockMvc.perform(get("/babies/{babyId}", BABY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.babyId").value(BABY_ID.intValue()))
                .andExpect(jsonPath("$.data.name").value("아기"))
                .andExpect(jsonPath("$.data.birthDate").value("2025-01-15"));

        verify(babyService).getBaby(BABY_ID, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    void malformedBabyIdReturnsBadRequestWithoutCallingService(String babyId) throws Exception {
        mockMvc.perform(get("/babies/{babyId}", babyId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("babyId"));

        verifyNoInteractions(babyService);
    }

    @Test
    void missingBabyReturnsNotFound() throws Exception {
        when(babyService.getBaby(BABY_ID, USER_ID)).thenThrow(new BabyNotFoundException());

        mockMvc.perform(get("/babies/{babyId}", BABY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        verify(babyService).getBaby(BABY_ID, USER_ID);
    }

    @Test
    void userWithoutAccessReturnsForbidden() throws Exception {
        when(babyService.getBaby(BABY_ID, USER_ID))
                .thenThrow(new AccessDeniedException("접근 권한이 없습니다."));

        mockMvc.perform(get("/babies/{babyId}", BABY_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(babyService).getBaby(BABY_ID, USER_ID);
    }
}
