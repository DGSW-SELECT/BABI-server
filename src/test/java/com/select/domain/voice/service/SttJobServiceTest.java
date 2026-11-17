package com.select.domain.voice.service;

import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.domain.baby.service.AccountContextService;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.entity.CareRecordType;
import com.select.domain.care.service.CareRecordWriter;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.AudioStorageException;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttJobNotCancelableException;
import com.select.domain.voice.exception.SttJobNotConfirmableException;
import com.select.domain.voice.exception.SttJobNotFoundException;
import com.select.domain.voice.exception.SttJobNotRetryableException;
import com.select.domain.voice.exception.SttJobLimitExceededException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttJobServiceTest {

    private static final Long BABY_ID = 1L;
    private static final Long FAMILY_MEMBER_ID = 7L;
    private static final Long USER_ID = 42L;
    private static final Long JOB_ID = 20L;
    private static final String AUDIO_PATH = "2026/11/03/audio.mp3";

    @Mock
    private SttJobRepository sttJobRepository;

    @Mock
    private VoiceInputRepository voiceInputRepository;

    @Mock
    private CareRecordWriter careRecordWriter;

    @Mock
    private AccountContextService accountContextService;

    @Mock
    private BabyRepository babyRepository;

    @Mock
    private UserRepository userRepository;

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
                Duration.ofDays(1),
                new SttJobProperties.Worker(5, Duration.ofSeconds(5), Duration.ofSeconds(10))
        );
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        sttJobService = new SttJobService(
                sttJobRepository,
                voiceInputRepository,
                careRecordWriter,
                accountContextService,
                babyRepository,
                userRepository,
                audioStorage,
                properties,
                transactionManager
        );
        lenient().when(babyRepository.findByIdForUpdate(BABY_ID))
                .thenReturn(Optional.of(com.select.domain.baby.entity.Baby.builder()
                        .name("테스트 아기")
                        .birthDate(java.time.LocalDate.of(2026, 1, 1))
                        .createdByUserId(USER_ID)
                        .build()));
        lenient().when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(User.builder()
                        .email("test@example.invalid")
                        .passwordHash("test")
                        .nickname("보호자")
                        .build()));
    }

    @Test
    void submitStoresAudioAndCreatesPendingJob() {
        MultipartFile audio = audio();
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(audioStorage.store(audio)).thenReturn(AUDIO_PATH);
        when(sttJobRepository.saveAndFlush(any(SttJob.class))).thenAnswer(invocation -> {
            SttJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", JOB_ID);
            return job;
        });

        SttJobResponse response = sttJobService.submit(BABY_ID, USER_ID, audio);

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
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());

        assertThatThrownBy(() -> sttJobService.submit(BABY_ID, USER_ID, audio))
                .isInstanceOf(InvalidAudioException.class);

        verifyNoInteractions(audioStorage, sttJobRepository);
    }

    @Test
    void submitDeletesStoredAudioWhenJobSaveFails() {
        MultipartFile audio = audio();
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(audioStorage.store(audio)).thenReturn(AUDIO_PATH);
        when(sttJobRepository.saveAndFlush(any(SttJob.class)))
                .thenThrow(new IllegalStateException("db error"));

        assertThatThrownBy(() -> sttJobService.submit(BABY_ID, USER_ID, audio))
                .isInstanceOf(IllegalStateException.class);

        verify(audioStorage).delete(AUDIO_PATH);
    }

    @Test
    void getReturnsCompletedJob() {
        SttJob job = completedJob(10L);
        when(accountContextService.resolveForRead(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.get(BABY_ID, USER_ID, JOB_ID);

        assertThat(response.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.transcript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.careRecords()).isEmpty();
    }

    @Test
    void getRejectsJobOwnedByAnotherMember() {
        SttJob job = completedJob(10L);
        ReflectionTestUtils.setField(job, "familyMemberId", 8L);
        when(accountContextService.resolveForRead(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.get(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMissingJobThrowsNotFound() {
        when(accountContextService.resolveForRead(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyId(JOB_ID, BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sttJobService.get(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotFoundException.class);
    }

    @Test
    void retryResetsFailedJob() {
        SttJob job = job(SttStatus.FAILED, 3);
        ReflectionTestUtils.setField(job, "lastError", "음성 변환에 실패했습니다.");
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));
        when(audioStorage.exists(AUDIO_PATH)).thenReturn(true);

        SttJobResponse response = sttJobService.retry(BABY_ID, USER_ID, JOB_ID);

        assertThat(response.status()).isEqualTo(SttStatus.PENDING);
        assertThat(response.attemptCount()).isZero();
        assertThat(response.errorMessage()).isNull();
        assertThat(job.getNextAttemptAt()).isNotNull();
    }

    @Test
    void retryRejectsJobOwnedByAnotherMember() {
        SttJob job = job(SttStatus.FAILED, 3);
        ReflectionTestUtils.setField(job, "familyMemberId", 8L);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.retry(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(audioStorage);
    }

    @Test
    void retryRejectsJobThatIsNotFailed() {
        SttJob job = completedJob(null);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.retry(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotRetryableException.class);

        verifyNoInteractions(audioStorage);
    }

    @Test
    void retryFailsWhenStoredAudioIsMissing() {
        SttJob job = job(SttStatus.FAILED, 3);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID)).thenReturn(Optional.of(job));
        when(audioStorage.exists(AUDIO_PATH)).thenReturn(false);

        assertThatThrownBy(() -> sttJobService.retry(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(AudioStorageException.class);
    }

    @Test
    void completeStoresTranscriptWithoutSavingVoiceInput() {
        SttJob job = job(SttStatus.PROCESSING, 1);
        when(sttJobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.complete(JOB_ID, "current-token", "  분유 120ml 먹였어요  ");

        assertThat(response.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.transcript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(response.voiceInputId()).isNull();
        assertThat(response.careRecords()).isEmpty();
        assertThat(job.getCompletedAt()).isNotNull();
        verifyNoInteractions(voiceInputRepository, careRecordWriter);
        verify(audioStorage).delete(AUDIO_PATH);
    }

    @Test
    void completeSkipsWhenJobIsNotProcessing() {
        SttJob job = completedJob(null);
        when(sttJobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));

        sttJobService.complete(JOB_ID, "current-token", "중복 완료");

        verifyNoInteractions(voiceInputRepository, careRecordWriter);
        verify(audioStorage, never()).delete(any());
    }

    @Test
    void completeIgnoresResultFromStaleAttempt() {
        SttJob job = job(SttStatus.PROCESSING, 2);
        when(sttJobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));

        sttJobService.complete(JOB_ID, "old-token", "오래된 시도 결과");

        assertThat(job.getStatus()).isEqualTo(SttStatus.PROCESSING);
        assertThat(job.getTranscript()).isNull();
        verify(audioStorage, never()).delete(any());
    }

    @Test
    void confirmCreatesVoiceInputAndCareRecords() {
        SttJob job = completedJob(null);
        LocalDateTime completedAt = job.getCompletedAt();
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));
        when(voiceInputRepository.save(any(VoiceInput.class))).thenAnswer(invocation -> {
            VoiceInput voiceInput = invocation.getArgument(0);
            ReflectionTestUtils.setField(voiceInput, "id", 10L);
            ReflectionTestUtils.setField(voiceInput, "createdAt", LocalDateTime.now());
            return voiceInput;
        });
        CareRecordResponse record = careRecord();
        when(careRecordWriter.analyzeAndSave(
                anyLong(), anyLong(), anyLong(), any(String.class), eq(completedAt)
        )).thenReturn(List.of(record));

        SttJobResponse response = sttJobService.confirm(BABY_ID, USER_ID, JOB_ID);

        assertThat(response.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.careRecords()).containsExactly(record);
        assertThat(job.getVoiceInputId()).isEqualTo(10L);
        verify(careRecordWriter).analyzeAndSave(
                BABY_ID, FAMILY_MEMBER_ID, 10L, "분유 120ml 먹였어요", completedAt
        );

        ArgumentCaptor<VoiceInput> captor = ArgumentCaptor.forClass(VoiceInput.class);
        verify(voiceInputRepository).save(captor.capture());
        assertThat(captor.getValue().getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(captor.getValue().getFamilyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
        assertThat(captor.getValue().getSttStatus()).isEqualTo(SttStatus.COMPLETED);
    }

    @Test
    void confirmRejectsJobOwnedByAnotherMember() {
        SttJob job = completedJob(null);
        ReflectionTestUtils.setField(job, "familyMemberId", 8L);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.confirm(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(voiceInputRepository, careRecordWriter);
    }

    @Test
    void confirmRejectsJobThatIsNotCompleted() {
        SttJob job = job(SttStatus.PROCESSING, 1);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.confirm(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotConfirmableException.class);

        verifyNoInteractions(voiceInputRepository, careRecordWriter);
    }

    @Test
    void confirmRejectsCompletedJobWithoutCompletionTime() {
        SttJob job = completedJob(null);
        ReflectionTestUtils.setField(job, "completedAt", null);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.confirm(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotConfirmableException.class);

        verifyNoInteractions(voiceInputRepository, careRecordWriter);
    }

    @Test
    void confirmRejectsAlreadyConfirmedJob() {
        SttJob job = completedJob(10L);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.confirm(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotConfirmableException.class);

        verifyNoInteractions(voiceInputRepository, careRecordWriter);
    }

    @Test
    void cancelDeletesUnconfirmedJobAndAudio() {
        SttJob job = completedJob(null);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        sttJobService.cancel(BABY_ID, USER_ID, JOB_ID);

        verify(sttJobRepository).delete(job);
        verify(audioStorage).delete(AUDIO_PATH);
    }

    @Test
    void cancelRejectsJobOwnedByAnotherMember() {
        SttJob job = completedJob(null);
        ReflectionTestUtils.setField(job, "familyMemberId", 8L);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.cancel(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(sttJobRepository, never()).delete(any());
    }

    @Test
    void cancelRejectsProcessingJob() {
        SttJob job = job(SttStatus.PROCESSING, 1);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.cancel(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotCancelableException.class);

        verify(sttJobRepository, never()).delete(any());
    }

    @Test
    void cancelRejectsConfirmedJob() {
        SttJob job = completedJob(10L);
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.findByIdAndBabyIdForUpdate(JOB_ID, BABY_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> sttJobService.cancel(BABY_ID, USER_ID, JOB_ID))
                .isInstanceOf(SttJobNotCancelableException.class);

        verify(sttJobRepository, never()).delete(any());
    }

    @Test
    void failSchedulesRetryWhenAttemptsRemain() {
        SttJob job = job(SttStatus.PROCESSING, 1);
        when(sttJobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.fail(JOB_ID, "current-token", "음성 변환에 실패했습니다.");

        assertThat(response.status()).isEqualTo(SttStatus.PENDING);
        assertThat(response.errorMessage()).isEqualTo("음성 변환에 실패했습니다.");
        assertThat(job.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(job.getCompletedAt()).isNull();
    }

    @Test
    void failMarksJobFailedAfterLastAttempt() {
        SttJob job = job(SttStatus.PROCESSING, 3);
        when(sttJobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));

        SttJobResponse response = sttJobService.fail(JOB_ID, "current-token", "음성 변환에 실패했습니다.");

        assertThat(response.status()).isEqualTo(SttStatus.FAILED);
        assertThat(job.getNextAttemptAt()).isNull();
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void cleanupExpiredFailedJobsRemovesJobAndAudioFile() {
        SttJob expired = job(SttStatus.FAILED, 3);
        when(sttJobRepository.findExpiredFailedJobs(
                eq(SttStatus.FAILED), any(LocalDateTime.class), any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(List.of(expired));

        int removed = sttJobService.cleanupExpiredFailedJobs();

        assertThat(removed).isEqualTo(1);
        verify(sttJobRepository).deleteAll(List.of(expired));
        verify(audioStorage).delete(AUDIO_PATH);
    }

    @Test
    void recoverStaleJobsRetriesOrFailsBasedOnAttemptCount() {
        SttJob retryable = job(SttStatus.PROCESSING, 1);
        SttJob exhausted = job(SttStatus.PROCESSING, 3);
        ReflectionTestUtils.setField(exhausted, "id", 21L);
        when(sttJobRepository.findStaleJobsForUpdate(any(), any()))
                .thenReturn(List.of(retryable, exhausted));

        int recovered = sttJobService.recoverStaleJobs();

        assertThat(recovered).isEqualTo(2);
        assertThat(retryable.getStatus()).isEqualTo(SttStatus.PENDING);
        assertThat(exhausted.getStatus()).isEqualTo(SttStatus.FAILED);
    }

    @Test
    void claimReturnsRepositoryResult() {
        when(sttJobRepository.claim(anyLong(), any(), any(), any(), any())).thenReturn(1);

        assertThat(sttJobService.claim(JOB_ID)).isNotBlank();
    }

    @Test
    void claimReturnsNullWhenAnotherWorkerAlreadyClaimedJob() {
        when(sttJobRepository.claim(anyLong(), any(), any(), any(), any())).thenReturn(0);

        assertThat(sttJobService.claim(JOB_ID)).isNull();
    }

    @Test
    void submitRejectsWhenBabyAlreadyHasMaximumActiveJobs() {
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.countByBabyIdAndStatusIn(eq(BABY_ID), any()))
                .thenReturn(10L);

        assertThatThrownBy(() -> sttJobService.submit(BABY_ID, USER_ID, audio()))
                .isInstanceOf(SttJobLimitExceededException.class);

        verifyNoInteractions(audioStorage);
    }

    @Test
    void submitRejectsWhenUserHasReachedDailyJobLimit() {
        when(accountContextService.resolveForWrite(USER_ID, BABY_ID)).thenReturn(context());
        when(sttJobRepository.countCreatedByUserSince(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(20L);

        assertThatThrownBy(() -> sttJobService.submit(BABY_ID, USER_ID, audio()))
                .isInstanceOf(SttJobLimitExceededException.class);

        verifyNoInteractions(audioStorage);
    }

    @Test
    void failIgnoresFailureFromStaleAttempt() {
        SttJob job = job(SttStatus.PROCESSING, 2);
        when(sttJobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));

        sttJobService.fail(JOB_ID, "old-token", "오래된 시도 실패");

        assertThat(job.getStatus()).isEqualTo(SttStatus.PROCESSING);
        assertThat(job.getLastError()).isNull();
    }

    private static AccountContextService.AccountContext context() {
        return new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID);
    }

    private static MultipartFile audio() {
        return new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static SttJob completedJob(Long voiceInputId) {
        SttJob job = job(SttStatus.COMPLETED, 1);
        job.complete("분유 120ml 먹였어요", LocalDateTime.now());
        if (voiceInputId != null) {
            job.confirm(voiceInputId, LocalDateTime.now());
        }
        return job;
    }

    private static SttJob job(SttStatus status, int attemptCount) {
        SttJob job = SttJob.builder()
                .babyId(BABY_ID)
                .familyMemberId(FAMILY_MEMBER_ID)
                .audioPath(AUDIO_PATH)
                .status(status)
                .attemptCount(attemptCount)
                .claimToken("current-token")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }

    private static CareRecordResponse careRecord() {
        return new CareRecordResponse(
                100L, BABY_ID, FAMILY_MEMBER_ID, "보호자", 10L, CareRecordType.FEEDING,
                LocalDateTime.of(2026, 10, 25, 12, 0), "FORMULA", 120, null,
                null, null, null, null, "분유 120ml 먹였어요",
                LocalDateTime.of(2026, 10, 25, 12, 0)
        );
    }
}
