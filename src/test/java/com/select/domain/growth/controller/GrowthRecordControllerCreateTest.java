package com.select.domain.growth.controller;

import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.service.GrowthService;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import com.select.global.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class GrowthRecordControllerCreateTest {
    private MockMvc mockMvc;
    private GrowthService growthService;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        growthService = mock(GrowthService.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        USER_ID, null, List.of()
                )
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new GrowthRecordController(growthService))
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setValidator(new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator())
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAcceptsJwtStringPrincipalAndDocumentedPath() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("10", null, List.of()));
        when(growthService.createGrowth(eq(1L), eq(10L), any())).thenReturn(
                new GrowthRecordCreateResponse(1L, 1L, new BigDecimal("72.50"), null, LocalDate.now(), null, LocalDateTime.now()));
        mockMvc.perform(post("/babies/1/growth-records").contentType(MediaType.APPLICATION_JSON)
                .content("{\"measuredDate\":\"" + LocalDate.now() + "\",\"heightCm\":72.5}"))
                .andExpect(status().isCreated());
        verify(growthService).createGrowth(eq(1L), eq(10L), any());
    }

    @Test
    void CREATE_GROWTH_SUCCESS() throws Exception {
        // given
        LocalDate measuredDate = LocalDate.now();

        when(growthService.createGrowth(
                eq(1L),
                eq(10L),
                any(GrowthRecordCreateRequest.class)
        )).thenReturn(
                new GrowthRecordCreateResponse(
                        1L,
                        1L,
                        new BigDecimal("72.50"),
                        new BigDecimal("9.25"),
                        measuredDate,
                        "정기 성장 측정",
                        LocalDateTime.now()
                )
        );

        // when & then
        mockMvc.perform(post("/babies/1/growth_records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "measuredDate": "%s",
                                    "heightCm": 72.50,
                                    "weightKg": 9.25,
                                    "memo": "정기 성장 측정"
                                }
                                """.formatted(measuredDate)))
                .andExpect(status().isCreated());

        verify(growthService).createGrowth(
                eq(1L),
                eq(10L),
                any(GrowthRecordCreateRequest.class)
        );
    }

    @Test
    void GROWTH_VALUE_REQUIRED_RETURN_400() throws Exception {
        when(growthService.createGrowth(
                eq(1L),
                eq(USER_ID),
                any(GrowthRecordCreateRequest.class)
        )).thenThrow(
                new BusinessException(ErrorCode.GROWTH_VALUE_REQUIRED)
        );

        mockMvc.perform(post("/babies/1/growth_records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "measuredDate": "%s",
                                "heightCm": null,
                                "weightKg": null,
                                "memo": "측정 테스트"
                            }
                            """.formatted(LocalDate.now())))
                .andExpect(status().isBadRequest());

        verify(growthService).createGrowth(
                eq(1L),
                eq(USER_ID),
                any(GrowthRecordCreateRequest.class)
        );
    }

    @Test
    void FUTURE_DATE_RETURN_400() throws Exception {
        // when & then
        mockMvc.perform(post("/babies/1/growth_records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "measuredDate": "%s",
                                    "heightCm": 72.50,
                                    "weightKg": 9.25,
                                    "memo": "미래 날짜 테스트"
                                }
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(growthService);
    }

    @Test
    void GROWTH_SERVICE_EXCEPTION_RETURN_ERROR() throws Exception {
        // given
        when(growthService.createGrowth(
                eq(1L),
                eq(10L),
                any(GrowthRecordCreateRequest.class)
        )).thenThrow(
                new BusinessException(ErrorCode.BABY_MEMBER_NOT_FOUND)
        );

        // when & then
        mockMvc.perform(post("/babies/1/growth_records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "measuredDate": "%s",
                                    "heightCm": 72.50,
                                    "weightKg": 9.25,
                                    "memo": "서비스 예외 테스트"
                                }
                                """.formatted(LocalDate.now())))
                .andExpect(status().isNotFound());
    }
}