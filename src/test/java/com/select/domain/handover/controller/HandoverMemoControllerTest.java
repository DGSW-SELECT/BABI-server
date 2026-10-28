package com.select.domain.handover.controller;

import com.select.domain.handover.dto.response.HandoverMemoResponse;
import com.select.domain.handover.service.HandoverMemoService;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HandoverMemoControllerTest {

    private MockMvc mockMvc;
    private HandoverMemoService handoverMemoService;

    @BeforeEach
    void setUp() {
        handoverMemoService = mock(HandoverMemoService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HandoverMemoController(handoverMemoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsMemoUsingAuthenticatedUserAttribute() throws Exception {
        when(handoverMemoService.createMemo(any(), any(), any(), any()))
                .thenReturn(new HandoverMemoResponse(
                        10L,
                        3L,
                        "분유 120ml 먹였습니다.",
                        LocalDateTime.of(2026, 10, 27, 12, 0)
                ));

        mockMvc.perform(post("/groups/1/babies/2/handover-memos")
                        .requestAttr("authenticatedUserId", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"분유 120ml 먹였습니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorId").value(3))
                .andExpect(jsonPath("$.data.content").value("분유 120ml 먹였습니다."));
    }

    @Test
    void rejectsMemoWithoutAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/groups/1/babies/2/handover-memos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"분유 120ml 먹였습니다.\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
