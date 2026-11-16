package com.select.domain.voice.controller;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecordType;
import com.select.domain.voice.dto.request.ParsedVoiceRecordRequest;
import com.select.domain.voice.dto.response.ParsedVoiceItemResponse;
import com.select.domain.voice.dto.response.ParsedVoiceRecordResponse;
import com.select.domain.voice.dto.response.VoiceRecordSavedResponse;
import com.select.domain.voice.service.VoiceRecordService;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoiceRecordAppControllerTest {

    private static final Long USER_ID = 42L;

    private MockMvc mockMvc;
    private VoiceRecordService voiceRecordService;

    @BeforeEach
    void setUp() {
        voiceRecordService = mock(VoiceRecordService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VoiceRecordAppController(voiceRecordService))
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
    void parseReturnsFrontendPreviewShape() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.webm", "audio/webm", "audio".getBytes(StandardCharsets.UTF_8)
        );
        ParsedVoiceRecordResponse response = new ParsedVoiceRecordResponse(
                "분유 120ml 먹였어",
                List.of(new ParsedVoiceItemResponse("feeding", "🍼 분유 120ml"))
        );
        when(voiceRecordService.parse(eq(USER_ID), eq(1L), any(MultipartFile.class)))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/voice-records/parse")
                        .file(audio)
                        .param("babyId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("분유 120ml 먹였어"))
                .andExpect(jsonPath("$.items[0].type").value("feeding"))
                .andExpect(jsonPath("$.items[0].label").value("🍼 분유 120ml"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(voiceRecordService).parse(eq(USER_ID), eq(1L), any(MultipartFile.class));
    }

    @Test
    void saveReturnsFrontendSavedShape() throws Exception {
        ParentingRecordResponse record = new ParentingRecordResponse(
                100L, 1L, 7L, 10L, ParentingRecordType.FEEDING,
                LocalDateTime.of(2026, 11, 16, 10, 0), 120, null,
                FeedingType.FORMULA, null, "분유 120ml 먹였어",
                LocalDateTime.of(2026, 11, 16, 10, 0)
        );
        when(voiceRecordService.saveParsed(eq(USER_ID), eq(1L), any(ParsedVoiceRecordRequest.class)))
                .thenReturn(new VoiceRecordSavedResponse(true, 10L, List.of(record)));

        mockMvc.perform(post("/api/voice-records")
                        .param("babyId", "1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transcript": "분유 120ml 먹였어",
                                  "items": [
                                    {"type": "feeding", "label": "🍼 분유 120ml"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.voiceInputId").value(10L))
                .andExpect(jsonPath("$.parentingRecords[0].recordType").value("FEEDING"))
                .andExpect(jsonPath("$.success").doesNotExist());

        verify(voiceRecordService)
                .saveParsed(eq(USER_ID), eq(1L), any(ParsedVoiceRecordRequest.class));
    }

    @Test
    void saveWithoutBabyIdUsesAuthenticatedUsersSingleBaby() throws Exception {
        when(voiceRecordService.saveParsed(eq(USER_ID), eq(null), any(ParsedVoiceRecordRequest.class)))
                .thenReturn(new VoiceRecordSavedResponse(true, 10L, List.of()));

        mockMvc.perform(post("/api/voice-records")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transcript": "분유 120ml 먹였어"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ok").value(true));

        verify(voiceRecordService)
                .saveParsed(eq(USER_ID), eq(null), any(ParsedVoiceRecordRequest.class));
    }

    @Test
    void saveWithBlankTranscriptReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/voice-records")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transcript": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("transcript"));

        verifyNoInteractions(voiceRecordService);
    }
}
