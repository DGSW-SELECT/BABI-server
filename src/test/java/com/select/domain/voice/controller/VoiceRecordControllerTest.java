package com.select.domain.voice.controller;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecordType;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.exception.SttJobNotFoundException;
import com.select.domain.voice.exception.SttJobNotRetryableException;
import com.select.domain.voice.exception.VoiceInputNotFoundException;
import com.select.domain.voice.service.SttJobService;
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
import java.util.List;

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
    private SttJobService sttJobService;

    @BeforeEach
    void setUp() {
        voiceRecordService = mock(VoiceRecordService.class);
        sttJobService = mock(SttJobService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VoiceRecordController(voiceRecordService, sttJobService))
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
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        ParentingRecordResponse parentingRecord = new ParentingRecordResponse(
                100L, 1L, 7L, 10L, ParentingRecordType.FEEDING,
                LocalDateTime.of(2026, 10, 25, 12, 0), 100, null,
                FeedingType.FORMULA, null, "분유 먹였어", createdAt
        );
        VoiceInputResponse response = new VoiceInputResponse(
                10L, 1L, 7L, "분유 먹였어", SttStatus.COMPLETED,
                createdAt, List.of(parentingRecord)
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
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.parentingRecords[0].parentingRecordId").value(100L))
                .andExpect(jsonPath("$.data.parentingRecords[0].recordType").value("FEEDING"))
                .andExpect(jsonPath("$.data.parentingRecords[0].amountMl").value(100))
                .andExpect(jsonPath("$.data.parentingRecords[0].feedingType").value("FORMULA"));

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
                LocalDateTime.of(2026, 10, 25, 12, 0), List.of()
        );
        when(voiceRecordService.get(1L, 10L)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.voiceInputId").value(10L))
                .andExpect(jsonPath("$.data.transcript").value("분유 먹였어"))
                .andExpect(jsonPath("$.data.parentingRecords").isEmpty());

        verify(voiceRecordService).get(1L, 10L);
    }

    @Test
    void getMissingVoiceInputReturnsNotFound() throws Exception {
        when(voiceRecordService.get(1L, 10L)).thenThrow(new VoiceInputNotFoundException());

        mockMvc.perform(get(BASE_URL + "/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VOICE_INPUT_NOT_FOUND"));
    }

    @Test
    void submitSttJobReturnsAccepted() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(sttJobService.submit(eq(1L), eq(7L), any(MultipartFile.class)))
                .thenReturn(sttJobResponse(SttStatus.PENDING, null, null));

        mockMvc.perform(multipart(BASE_URL + "/stt-jobs")
                        .file(audio)
                        .param("familyMemberId", "7"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(20L))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.maxAttempts").value(3));

        verify(sttJobService).submit(eq(1L), eq(7L), any(MultipartFile.class));
    }

    @Test
    void submitSttJobWithoutFamilyMemberIdReturnsBadRequest() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(BASE_URL + "/stt-jobs").file(audio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(sttJobService);
    }

    @Test
    void submitSttJobWithNonPositiveFamilyMemberIdReturnsBadRequest() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(BASE_URL + "/stt-jobs")
                        .file(audio)
                        .param("familyMemberId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(sttJobService);
    }

    @Test
    void getSttJobReturnsCompletedJobAndParentingRecords() throws Exception {
        ParentingRecordResponse parentingRecord = new ParentingRecordResponse(
                100L, 1L, 7L, 10L, ParentingRecordType.FEEDING,
                LocalDateTime.of(2026, 10, 25, 12, 0), 120, null,
                FeedingType.FORMULA, null, "분유 120ml 먹였어요", LocalDateTime.of(2026, 10, 25, 12, 0)
        );
        SttJobResponse response = new SttJobResponse(
                20L, 1L, 7L, SttStatus.COMPLETED, "분유 120ml 먹였어요", 10L,
                1, 3, null, LocalDateTime.of(2026, 10, 25, 12, 0),
                LocalDateTime.of(2026, 10, 25, 12, 1), List.of(parentingRecord)
        );
        when(sttJobService.get(1L, 20L)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/stt-jobs/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.transcript").value("분유 120ml 먹였어요"))
                .andExpect(jsonPath("$.data.voiceInputId").value(10L))
                .andExpect(jsonPath("$.data.parentingRecords[0].parentingRecordId").value(100L));

        verify(sttJobService).get(1L, 20L);
    }

    @Test
    void getMissingSttJobReturnsNotFound() throws Exception {
        when(sttJobService.get(1L, 20L)).thenThrow(new SttJobNotFoundException());

        mockMvc.perform(get(BASE_URL + "/stt-jobs/20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STT_JOB_NOT_FOUND"));
    }

    @Test
    void retrySttJobReturnsPendingJob() throws Exception {
        when(sttJobService.retry(1L, 20L)).thenReturn(sttJobResponse(SttStatus.PENDING, null, null));

        mockMvc.perform(post(BASE_URL + "/stt-jobs/20/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(sttJobService).retry(1L, 20L);
    }

    @Test
    void retryNonFailedSttJobReturnsConflict() throws Exception {
        when(sttJobService.retry(1L, 20L))
                .thenThrow(new SttJobNotRetryableException("실패한 작업만 재시도할 수 있습니다."));

        mockMvc.perform(post(BASE_URL + "/stt-jobs/20/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STT_JOB_NOT_RETRYABLE"));
    }

    private static SttJobResponse sttJobResponse(
            SttStatus status,
            String transcript,
            Long voiceInputId
    ) {
        return new SttJobResponse(
                20L, 1L, 7L, status, transcript, voiceInputId,
                0, 3, null, LocalDateTime.of(2026, 10, 25, 12, 0), null, List.of()
        );
    }
}
