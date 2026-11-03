package com.select.domain.voice.service;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecordType;
import com.select.domain.parenting.service.ParentingRecordService;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.AudioStorageException;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttJobNotFoundException;
import com.select.domain.voice.exception.SttJobNotRetryableException;
import com.select.domain.voice.repository.SttJobRepository;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.storage.AudioStorage;
import com.select.global.config.SttJobProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttJobServiceTest {

    private static final Long BABY_ID = 1L;
    private static final Long FAMILY_MEMBER_ID = 7L;
    private static final Long JOB_ID = 20L;
    private static final String AUDIO_PATH = "2026/11/03/audio.mp3";

    @Mock
    private SttJobRepository sttJobRepository;

    @Mock
    private VoiceInputRepository voiceInputRepository;

    @Mock
    private ParentingRecordService parentingRecordService;

    @Mock
    private AudioStorage audioStorage;

    private SttJobService sttJobService;

    @BeforeEach
    void setUp() {
        SttJobProperties properties = new SttJobProperties(
                "/tmp/babi-test-stt",
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                new SttJobProperties.Worker(5, Duration.ofSeconds(5), Duration.ofSeconds(10))
        );
        sttJobService = new SttJobService(
                sttJobRepository,
                voiceInputRepository,
                parentingRecordService,
                audioStorage,
                properties
        );
    }

    @Test
    void submitStoresAudioAndCreatesPendingJob() {
        MultipartFile audio = audio();
        when(audioStorage.store(audio)).thenReturn(AUDIO_PATH);
        when(sttJobRepository.saveAndFlush(any(SttJob.class))).thenAnswer(invocation -> {
            SttJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", JOB_ID);
            return job;
        });

        SttJobResponse response = sttJobService.submit(BABY_ID, FAMILY_MEMBER_ID, audio);

        ArgumentCaptor<SttJob> captor = ArgumentCaptor.forClass(SttJob.class);
        verify(sttJobRepository).saveAndFlush(captor.capture());
        SttJob saved = captor.getValue();

        assertThat(saved.getBabyId()).isEqualTo(BABY_ID);
        assertThat(saved.getFamilyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
        assertThat(saved.getAudioPath()).isEqualTo(AUDIO_PATH);
        assertThat(saved.getStatus()).isEqualTo(SttStatus.PENDING);
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getNextAttemptAt()).isNotNull();

        assertThat(response.jobId()).isEqualTo(JOB_ID);
        assertThat(response.status()).isEqualTo(SttStatus.PENDING);
        assertThat(response.maxAttempts()).isEqualTo(3);
    }

    @Test
    void submitRejectsInvalidAudioBeforeStoring() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.txt", "text/plain", "not-audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> sttJobService.submit(BABY_ID, FAMILY_MEMBER_ID, audio))
                .isInstanceOf(InvalidAudioException.class);

        verifyNoInteractions(audioStorage, sttJobRepository);
    }

    @Test
    void submitDeletesStoredAudioWhenJobSaveFails() {
        MultipartFile audio = audio();
        when(audioStorage.store(audio)).thenReturn(AUDIO_PATH);
        when(sttJobRepository.saveAndFlush(any(SttJob.class)))
                .thenThrow(new IllegalStateException("db error"));

        assertThatThrownBy(() -> sttJobService.submit(BABY_ID, FAMILY_MEMBER_ID, audio))
                .isInstanceOf(IllegalStateException.class);

        verify(audioStorage).delete(AUDIO_PATH);
    }

    @Test
    void getReturnsCompletedJobWithParentingRecords() {
        SttJob job = job(SttStatus.COMPLETED, 1);
        job.complete("분유 120ml 먹였어요", 10L, LocalDateTime.now());
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        ParentingRecordResponse record = parentingRecord();
        when(parentingRecordService.findByVoiceInput(BABY_ID, 10L)).thenReturn(List.of(record));

        SttJobResponse response = sttJobService.get(BABY_ID, JOB_ID);

        assertThat(response.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.transcript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.parentingRecords()).containsExactly(record);
    }

    @Test
    void getPendingJobDoesNotLookUpParentingRecords() {
        SttJob job = job(SttStatus.PENDING, 0);
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.get(BABY_ID, JOB_ID);

        assertThat(response.parentingRecords()).isEmpty();
        verifyNoInteractions(parentingRecordService);
    }

    @Test
    void getMissingJobThrowsNotFound() {
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sttJobService.get(BABY_ID, JOB_ID))
                .isInstanceOf(SttJobNotFoundException.class);
    }

    @Test
    void retryResetsFailedJob() {
        SttJob job = SttJob.builder()
                .babyId(BABY_ID)
                .familyMemberId(FAMILY_MEMBER_ID)
                .audioPath(AUDIO_PATH)
                .status(SttStatus.FAILED)
                .attemptCount(3)
                .lastError("음성 변환에 실패했습니다.")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));
        when(audioStorage.exists(AUDIO_PATH)).thenReturn(true);

        SttJobResponse response = sttJobService.retry(BABY_ID, JOB_ID);

        assertThat(response.status()).isEqualTo(SttStatus.PENDING);
        assertThat(response.attemptCount()).isZero();
        assertThat(response.errorMessage()).isNull();
        assertThat(job.getNextAttemptAt()).isNotNull();
    }

    @Test
    void retryRejectsJobThatIsNotFailed() {
        SttJob job = job(SttStatus.COMPLETED, 1);
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.retry(BABY_ID, JOB_ID))
                .isInstanceOf(SttJobNotRetryableException.class);

        verifyNoInteractions(audioStorage);
    }

    @Test
    void retryFailsWhenStoredAudioIsMissing() {
        SttJob job = job(SttStatus.FAILED, 3);
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));
        when(audioStorage.exists(AUDIO_PATH)).thenReturn(false);

        assertThatThrownBy(() -> sttJobService.retry(BABY_ID, JOB_ID))
                .isInstanceOf(AudioStorageException.class);
    }

    @Test
    void completeCreatesVoiceInputAndParentingRecords() {
        SttJob job = job(SttStatus.PROCESSING, 1);
        when(sttJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(voiceInputRepository.save(any(VoiceInput.class))).thenAnswer(invocation -> {
            VoiceInput voiceInput = invocation.getArgument(0);
            ReflectionTestUtils.setField(voiceInput, "id", 10L);
            ReflectionTestUtils.setField(voiceInput, "createdAt", LocalDateTime.now());
            return voiceInput;
        });
        ParentingRecordResponse record = parentingRecord();
        when(parentingRecordService.analyzeAndSave(
                anyLong(), anyLong(), anyLong(), any(String.class), any(LocalDateTime.class)
        )).thenReturn(List.of(record));

        SttJobResponse response = sttJobService.complete(JOB_ID, "  분유 120ml 먹였어요  ");

        assertThat(response.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.transcript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.parentingRecords()).containsExactly(record);
        assertThat(job.getCompletedAt()).isNotNull();

        ArgumentCaptor<VoiceInput> captor = ArgumentCaptor.forClass(VoiceInput.class);
        verify(voiceInputRepository).save(captor.capture());
        assertThat(captor.getValue().getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(captor.getValue().getSttStatus()).isEqualTo(SttStatus.COMPLETED);
        verify(audioStorage).delete(AUDIO_PATH);
    }

    @Test
    void completeSkipsWhenJobIsNotProcessing() {
        SttJob job = job(SttStatus.COMPLETED, 1);
        when(sttJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        sttJobService.complete(JOB_ID, "중복 완료");

        verifyNoInteractions(voiceInputRepository, parentingRecordService);
        verify(audioStorage, never()).delete(any());
    }

    @Test
    void failSchedulesRetryWhenAttemptsRemain() {
        SttJob job = job(SttStatus.PROCESSING, 1);
        when(sttJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.fail(JOB_ID, "음성 변환에 실패했습니다.");

        assertThat(response.status()).isEqualTo(SttStatus.PENDING);
        assertThat(response.errorMessage()).isEqualTo("음성 변환에 실패했습니다.");
        assertThat(job.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(job.getCompletedAt()).isNull();
    }

    @Test
    void failMarksJobFailedAfterLastAttempt() {
        SttJob job = job(SttStatus.PROCESSING, 3);
        when(sttJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.fail(JOB_ID, "음성 변환에 실패했습니다.");

        assertThat(response.status()).isEqualTo(SttStatus.FAILED);
        assertThat(job.getNextAttemptAt()).isNull();
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void recoverStaleJobsRetriesOrFailsBasedOnAttemptCount() {
        SttJob retryable = job(SttStatus.PROCESSING, 1);
        SttJob exhausted = job(SttStatus.PROCESSING, 3);
        ReflectionTestUtils.setField(exhausted, "id", 21L);
        when(sttJobRepository.findAllByStatusAndStartedAtBefore(any(), any()))
                .thenReturn(List.of(retryable, exhausted));

        int recovered = sttJobService.recoverStaleJobs();

        assertThat(recovered).isEqualTo(2);
        assertThat(retryable.getStatus()).isEqualTo(SttStatus.PENDING);
        assertThat(exhausted.getStatus()).isEqualTo(SttStatus.FAILED);
    }

    @Test
    void claimReturnsRepositoryResult() {
        when(sttJobRepository.claim(anyLong(), any(), any(), any())).thenReturn(1);

        assertThat(sttJobService.claim(JOB_ID)).isTrue();
    }

    private static MultipartFile audio() {
        return new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static SttJob job(SttStatus status, int attemptCount) {
        SttJob job = SttJob.builder()
                .babyId(BABY_ID)
                .familyMemberId(FAMILY_MEMBER_ID)
                .audioPath(AUDIO_PATH)
                .status(status)
                .attemptCount(attemptCount)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }

    private static ParentingRecordResponse parentingRecord() {
        return new ParentingRecordResponse(
                100L, BABY_ID, FAMILY_MEMBER_ID, 10L, ParentingRecordType.FEEDING,
                LocalDateTime.of(2026, 10, 25, 12, 0), 120, null,
                FeedingType.FORMULA, null, "분유 120ml 먹였어요",
                LocalDateTime.of(2026, 10, 25, 12, 0)
        );
    }
}
