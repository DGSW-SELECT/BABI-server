package com.select.domain.handover.controller;

import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.service.HandoverBriefingService;
import com.select.domain.handover.exception.HandoverBriefingRateLimitedException;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HandoverBriefingControllerTest {

    private MockMvc mockMvc;
    private HandoverBriefingService handoverBriefingService;

    @BeforeEach
    void setUp() {
        handoverBriefingService = mock(HandoverBriefingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HandoverBriefingController(handoverBriefingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsBriefingWithoutRequestBody() throws Exception {
        when(handoverBriefingService.createBriefing(10L, 2L))
                .thenReturn(new HandoverBriefingResponse(
                        LocalDate.of(2026, 10, 27),
                        "오늘 수유 기록을 정리했습니다."
                ));

        mockMvc.perform(post("/babi/api/babies/2/handover-briefings")
                        .requestAttr("userId", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.briefingDate").value("2026-10-27"))
                .andExpect(jsonPath("$.data.summary")
                        .value("오늘 수유 기록을 정리했습니다."));
    }

    @Test
    void getsLatestBriefing() throws Exception {
        when(handoverBriefingService.getLatestBriefing(10L, 2L))
                .thenReturn(new HandoverBriefingResponse(
                        LocalDate.of(2026, 10, 27),
                        "저장된 최신 브리핑"
                ));

        mockMvc.perform(get("/babi/api/babies/2/handover-briefings/latest")
                        .requestAttr("userId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.briefingDate").value("2026-10-27"))
                .andExpect(jsonPath("$.data.summary").value("저장된 최신 브리핑"));
    }

    @Test
    void repeatedGenerationReturns429AndRetryAfter() throws Exception {
        when(handoverBriefingService.createBriefing(10L, 2L))
                .thenThrow(new HandoverBriefingRateLimitedException(60));

        mockMvc.perform(post("/babi/api/babies/2/handover-briefings")
                        .requestAttr("userId", "10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HANDOVER_BRIEFING_RATE_LIMITED"));
    }
}
