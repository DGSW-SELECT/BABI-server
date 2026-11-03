package com.select.domain.voice.service;

import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.storage.AudioStorage;
import com.select.global.config.SttJobProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttJobWorkerTest {

    private static final Long JOB_ID = 20L;
    private static final String AUDIO_PATH = "2026/11/03/audio.mp3";

    @Mock
    private SttJobService sttJobService;

    @Mock
    private SttClient sttClient;

    @Mock
    private AudioStorage audioStorage;

    private SttJobWorker sttJobWorker;

    @BeforeEach
    void setUp() {
        SttJobProperties properties = new SttJobProperties(
                "/tmp/babi-test-stt",
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                new SttJobProperties.Worker(5, Duration.ofSeconds(5), Duration.ofSeconds(10))
        );
        sttJobWorker = new SttJobWorker(sttJobService, sttClient, audioStorage, properties);
    }

    @Test
    void processTranscribesAudioAndCompletesJob() {
        SttJob job = job(SttStatus.PROCESSING);
        MultipartFile audio = org.mockito.Mockito.mock(MultipartFile.class);
        when(sttJobService.findForProcessing(JOB_ID)).thenReturn(job);
        when(audioStorage.load(AUDIO_PATH)).thenReturn(audio);
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("  분유 120ml 먹였어요  "));

        sttJobWorker.process(JOB_ID);

        verify(sttJobService).complete(JOB_ID, "분유 120ml 먹였어요");
        verify(sttJobService, never()).fail(any(), anyString());
    }

    @Test
    void processFailsJobWhenTranscriptIsBlank() {
        SttJob job = job(SttStatus.PROCESSING);
        MultipartFile audio = org.mockito.Mockito.mock(MultipartFile.class);
        when(sttJobService.findForProcessing(JOB_ID)).thenReturn(job);
        when(audioStorage.load(AUDIO_PATH)).thenReturn(audio);
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("   "));

        sttJobWorker.process(JOB_ID);

        verify(sttJobService).fail(JOB_ID, "음성을 텍스트로 변환하지 못했습니다.");
        verify(sttJobService, never()).complete(any(), anyString());
    }

    @Test
    void processFailsJobWhenSttThrows() {
        SttJob job = job(SttStatus.PROCESSING);
        MultipartFile audio = org.mockito.Mockito.mock(MultipartFile.class);
        when(sttJobService.findForProcessing(JOB_ID)).thenReturn(job);
        when(audioStorage.load(AUDIO_PATH)).thenReturn(audio);
        when(sttClient.transcribe(audio))
                .thenThrow(new SttFailedException("음성 변환에 실패했습니다."));

        sttJobWorker.process(JOB_ID);

        verify(sttJobService).fail(JOB_ID, "음성 변환에 실패했습니다.");
        verify(sttJobService, never()).complete(any(), anyString());
    }

    @Test
    void processSkipsJobThatIsNotProcessing() {
        when(sttJobService.findForProcessing(JOB_ID)).thenReturn(job(SttStatus.PENDING));

        sttJobWorker.process(JOB_ID);

        verifyNoInteractions(audioStorage, sttClient);
        verify(sttJobService, never()).complete(any(), anyString());
        verify(sttJobService, never()).fail(any(), anyString());
    }

    @Test
    void pollProcessesOnlyClaimedJobs() {
        when(sttJobService.recoverStaleJobs()).thenReturn(0);
        when(sttJobService.findReadyJobIds(5)).thenReturn(List.of(20L, 21L));
        when(sttJobService.claim(20L)).thenReturn(true);
        when(sttJobService.claim(21L)).thenReturn(false);
        when(sttJobService.findForProcessing(20L)).thenReturn(null);

        sttJobWorker.poll();

        verify(sttJobService).claim(20L);
        verify(sttJobService).claim(21L);
        verify(sttJobService).findForProcessing(20L);
        verify(sttJobService, never()).findForProcessing(21L);
    }

    private static SttJob job(SttStatus status) {
        SttJob job = SttJob.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .audioPath(AUDIO_PATH)
                .status(status)
                .attemptCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }
}
