package com.select.domain.voice.controller;

import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.exception.VoiceInputNotFoundException;
import com.select.domain.voice.service.VoiceRecordService;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoiceRecordControllerTest {

    private static final String BASE_URL = "/api/v1/babies/1/voice-records";

    private MockMvc mockMvc;
    private VoiceRecordService voiceRecordService;

    @BeforeEach
    void setUp() {
        voiceRecordService = mock(VoiceRecordService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VoiceRecordController(voiceRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void transcribeReturnsTranscript() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(voiceRecordService.transcribe(eq(1L), any(MultipartFile.class)))
                .thenReturn(new SttTranscriptResponse("분유 먹였어"));

        mockMvc.perform(multipart(BASE_URL + "/stt").file(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transcript").value("분유 먹였어"));

        verify(voiceRecordService).transcribe(eq(1L), any(MultipartFile.class));
    }

    @Test
    void missingAudioPartReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart(BASE_URL + "/stt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(voiceRecordService);
    }

    @Test
    void invalidAudioReturnsBadRequest() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.txt", "text/plain", "not-audio".getBytes(StandardCharsets.UTF_8)
        );
        when(voiceRecordService.transcribe(eq(1L), any(MultipartFile.class)))
                .thenThrow(new InvalidAudioException("지원하지 않는 음성 파일 형식입니다."));

        mockMvc.perform(multipart(BASE_URL + "/stt").file(audio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AUDIO"));
    }

    @Test
    void sttFailureReturnsBadGateway() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(voiceRecordService.transcribe(eq(1L), any(MultipartFile.class)))
                .thenThrow(new SttFailedException("음성 변환에 실패했습니다."));

        mockMvc.perform(multipart(BASE_URL + "/stt").file(audio))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("STT_FAILED"));
    }

    @Test
    void saveReturnsCreatedVoiceInput() throws Exception {
        VoiceInputResponse response = new VoiceInputResponse(
                10L, 1L, 7L, "분유 먹였어", SttStatus.COMPLETED,
                LocalDateTime.of(2026, 10, 25, 12, 0)
        );
        when(voiceRecordService.save(eq(1L), any(VoiceRecordRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "transcript": "분유 먹였어",
                                  "familyMemberId": 7
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.voiceInputId").value(10L))
                .andExpect(jsonPath("$.data.babyId").value(1L))
                .andExpect(jsonPath("$.data.familyMemberId").value(7L))
                .andExpect(jsonPath("$.data.transcript").value("분유 먹였어"))
                .andExpect(jsonPath("$.data.sttStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        verify(voiceRecordService).save(eq(1L), any(VoiceRecordRequest.class));
    }

    @Test
    void saveWithBlankTranscriptReturnsValidationError() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "transcript": "   ",
                                  "familyMemberId": 7
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("transcript"));

        verifyNoInteractions(voiceRecordService);
    }

    @Test
    void saveWithoutFamilyMemberIdReturnsValidationError() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "transcript": "분유 먹였어"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("familyMemberId"));

        verifyNoInteractions(voiceRecordService);
    }

    @Test
    void getReturnsVoiceInput() throws Exception {
        VoiceInputResponse response = new VoiceInputResponse(
                10L, 1L, 7L, "분유 먹였어", SttStatus.COMPLETED,
                LocalDateTime.of(2026, 10, 25, 12, 0)
        );
        when(voiceRecordService.get(1L, 10L)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.voiceInputId").value(10L))
                .andExpect(jsonPath("$.data.transcript").value("분유 먹였어"));

        verify(voiceRecordService).get(1L, 10L);
    }

    @Test
    void getMissingVoiceInputReturnsNotFound() throws Exception {
        when(voiceRecordService.get(1L, 10L)).thenThrow(new VoiceInputNotFoundException());

        mockMvc.perform(get(BASE_URL + "/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VOICE_INPUT_NOT_FOUND"));
    }
}
