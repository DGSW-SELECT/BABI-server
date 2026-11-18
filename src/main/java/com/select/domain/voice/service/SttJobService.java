package com.select.domain.voice.service;

import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.service.AccountContextService;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.service.CareRecordWriter;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.SttJobLimitExceededException;
import com.select.domain.voice.exception.SttJobNotConfirmableException;
import com.select.domain.voice.exception.SttJobNotFoundException;
import com.select.domain.voice.repository.SttJobRepository;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.storage.AudioStorage;
import com.select.domain.voice.support.AudioFileSupport;
import com.select.global.config.SttJobProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 비동기 STT 작업의 제출·조회·확인 저장과 상태 전이를 담당한다.
 *
 * <p>실제 STT 호출은 {@link SttJobWorker}가 맡고, 이 서비스는 DB 트랜잭션 경계를
 * 보장한다. STT 호출 동안 트랜잭션을 열어 두지 않기 위해 처리 흐름을 분리했다.
 * STT가 끝나도 사용자가 확인하기 전에는 {@link VoiceInput}을 만들지 않는다.</p>
 */
@Service
public class SttJobService {

    private static final Logger log = LoggerFactory.getLogger(SttJobService.class);
    private static final int MAX_ACTIVE_JOBS_PER_BABY = 10;
    private static final int MAX_JOBS_PER_USER_PER_DAY = 20;
    private static final Set<SttStatus> ACTIVE_JOB_STATUSES = Set.of(SttStatus.PENDING, SttStatus.PROCESSING);

    private final SttJobRepository sttJobRepository;
    private final VoiceInputRepository voiceInputRepository;
    private final CareRecordWriter careRecordWriter;
    private final AccountContextService accountContextService;
    private final BabyRepository babyRepository;
    private final UserRepository userRepository;
    private final AudioStorage audioStorage;
    private final SttRequestRateLimiter sttRequestRateLimiter;
    private final SttJobProperties properties;
    private final TransactionTemplate transactions;

    public SttJobService(
            SttJobRepository sttJobRepository,
            VoiceInputRepository voiceInputRepository,
            CareRecordWriter careRecordWriter,
            AccountContextService accountContextService,
            BabyRepository babyRepository,
            UserRepository userRepository,
            AudioStorage audioStorage,
            SttRequestRateLimiter sttRequestRateLimiter,
            SttJobProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.sttJobRepository = sttJobRepository;
        this.voiceInputRepository = voiceInputRepository;
        this.careRecordWriter = careRecordWriter;
        this.accountContextService = accountContextService;
        this.babyRepository = babyRepository;
        this.userRepository = userRepository;
        this.audioStorage = audioStorage;
        this.sttRequestRateLimiter = sttRequestRateLimiter;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * 음성 파일을 저장하고 STT 작업을 {@code PENDING} 상태로 등록한다.
     */
    @Transactional(propagation = Propagation.NEVER)
    public SttJobResponse submit(Long babyId, Long userId, MultipartFile audio) {
        AccountContextService.AccountContext prechecked =
                accountContextService.resolveForWrite(userId, babyId);
        AudioFileSupport.validate(audio);
        ensureCapacity(userId, prechecked.babyId());
        sttRequestRateLimiter.consume(userId);
        String audioPath = audioStorage.store(audio);

        try {
            return transactions.execute(status -> {
                userRepository.findByIdForUpdate(userId)
                        .filter(User::isActive)
                        .orElseThrow(() -> new AccessDeniedException("활성 계정이 필요합니다."));
                babyRepository.findByIdForUpdate(prechecked.babyId())
                        .orElseThrow(BabyNotFoundException::new);
                AccountContextService.AccountContext context =
                        accountContextService.resolveForWrite(userId, prechecked.babyId());
                ensureCapacity(userId, context.babyId());
                SttJob job = sttJobRepository.saveAndFlush(SttJob.pending(
                        context.babyId(),
                        context.memberId(),
                        audioPath,
                        LocalDateTime.now()
                ));
                log.info("STT 작업이 등록되었습니다. jobId={}, babyId={}", job.getId(), context.babyId());
                return SttJobResponse.of(job, properties.maxAttempts());
            });
        } catch (RuntimeException e) {
            deleteQuietly(audioPath);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public SttJobResponse get(Long babyId, Long userId, Long jobId) {
        AccountContextService.AccountContext context =
                accountContextService.resolveForRead(userId, babyId);
        SttJob job = findJob(context.babyId(), jobId);
        requireOwner(job, context.memberId());
        return SttJobResponse.of(job, properties.maxAttempts());
    }

    @Transactional(readOnly = true)
    public List<Long> findReadyJobIds(int limit) {
        int pageSize = Math.max(1, limit);
        return sttJobRepository.findReadyIds(
                SttStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, pageSize)
        );
    }

    /**
     * 작업을 원자적으로 선점하고 해당 처리 시도에만 유효한 claim 토큰을 반환한다.
     * 다른 인스턴스가 먼저 가져갔으면 {@code null}을 반환한다.
     */
    @Transactional
    public String claim(Long jobId) {
        String claimToken = UUID.randomUUID().toString();
        int claimed = sttJobRepository.claim(
                jobId,
                SttStatus.PROCESSING,
                SttStatus.PENDING,
                LocalDateTime.now(),
                claimToken
        );
        return claimed == 1 ? claimToken : null;
    }

    @Transactional(readOnly = true)
    public SttJob findForProcessing(Long jobId) {
        return sttJobRepository.findById(jobId).orElse(null);
    }

    /**
     * STT 성공 결과만 작업에 기록한다. 음성 기록 저장은 사용자가 확인한 뒤
     * {@link #confirm(Long, Long, Long)}에서 수행한다.
     */
    @Transactional
    public SttJobResponse complete(Long jobId, String claimToken, String transcript) {
        SttJob job = sttJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(SttJobNotFoundException::new);
        if (!isCurrentAttempt(job, claimToken)) {
            log.warn("현재 claim이 아니어서 완료 처리를 건너뜁니다. jobId={}, status={}",
                    jobId, job.getStatus());
            return SttJobResponse.of(job, properties.maxAttempts());
        }

        String trimmedTranscript = transcript.trim();
        job.complete(trimmedTranscript, LocalDateTime.now());
        deleteAfterCommit(job.getAudioPath());
        log.info("STT 작업이 완료되었습니다. jobId={}", jobId);
        return SttJobResponse.of(job, properties.maxAttempts());
    }

    /**
     * 사용자가 미리보기에서 확인한 STT 결과를 음성 기록과 육아기록으로 저장한다.
     */
    @Transactional(propagation = Propagation.NEVER)
    public SttJobResponse confirm(Long babyId, Long userId, Long jobId) {
        AccountContextService.AccountContext prechecked =
                accountContextService.resolveForWrite(userId, babyId);
        return transactions.execute(status -> {
            babyRepository.findByIdForUpdate(prechecked.babyId())
                    .orElseThrow(BabyNotFoundException::new);
            AccountContextService.AccountContext context =
                    accountContextService.resolveForWrite(userId, prechecked.babyId());
            SttJob job = sttJobRepository.findByIdAndBabyIdForUpdate(jobId, context.babyId())
                    .orElseThrow(SttJobNotFoundException::new);
            requireOwner(job, context.memberId());
            if (job.getStatus() != SttStatus.COMPLETED
                    || job.getVoiceInputId() != null
                    || !StringUtils.hasText(job.getTranscript())
                    || job.getCompletedAt() == null) {
                throw new SttJobNotConfirmableException("완료된 음성 변환 작업만 저장할 수 있습니다.");
            }

            VoiceInput voiceInput = voiceInputRepository.save(VoiceInput.builder()
                    .babyId(job.getBabyId())
                    .familyMemberId(job.getFamilyMemberId())
                    .transcript(job.getTranscript())
                    .sttStatus(SttStatus.COMPLETED)
                    .build());

            List<CareRecordResponse> careRecords = careRecordWriter.analyzeAndSave(
                    job.getBabyId(),
                    job.getFamilyMemberId(),
                    voiceInput.getId(),
                    job.getTranscript(),
                    job.getCompletedAt()
            );

            job.confirm(voiceInput.getId(), LocalDateTime.now());
            log.info("STT 작업을 사용자가 확인해 저장했습니다. jobId={}, voiceInputId={}",
                    jobId, voiceInput.getId());
            return SttJobResponse.of(job, properties.maxAttempts(), careRecords);
        });
    }

    /**
     * STT 실패를 기록하고 남은 재시도 횟수에 따라 {@code PENDING} 또는 {@code FAILED}로 전이한다.
     */
    @Transactional
    public SttJobResponse fail(Long jobId, String claimToken, String error) {
        SttJob job = sttJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(SttJobNotFoundException::new);
        if (!isCurrentAttempt(job, claimToken)) {
            log.warn("현재 claim이 아니어서 실패 처리를 건너뜁니다. jobId={}, status={}",
                    jobId, job.getStatus());
            return SttJobResponse.of(job, properties.maxAttempts());
        }

        LocalDateTime now = LocalDateTime.now();
        boolean retryable = job.getAttemptCount() < properties.maxAttempts();
        if (retryable) {
            job.fail(error, now.plus(properties.retryDelay()), now);
            log.warn("STT 작업이 실패해 재시도합니다. jobId={}, attempt={}/{}, error={}",
                    jobId, job.getAttemptCount(), properties.maxAttempts(), error);
        } else {
            job.fail(error, null, now);
            log.error("STT 작업이 최종 실패했습니다. jobId={}, attempts={}, error={}",
                    jobId, job.getAttemptCount(), error);
        }
        return SttJobResponse.of(job, properties.maxAttempts());
    }

    /**
     * 처리 중 서버가 중단되어 오래 남은 작업을 복구한다.
     */
    @Transactional
    public int recoverStaleJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<SttJob> staleJobs = sttJobRepository.findStaleJobsForUpdate(
                SttStatus.PROCESSING,
                now.minus(properties.staleTimeout())
        );

        for (SttJob job : staleJobs) {
            boolean retryable = job.getAttemptCount() < properties.maxAttempts();
            job.recoverStale("처리 시간이 초과되어 작업을 복구했습니다.", retryable, now);
            log.warn("멈춘 STT 작업을 복구했습니다. jobId={}, retryable={}", job.getId(), retryable);
        }
        return staleJobs.size();
    }

    /**
     * 최종 실패 작업과 음성 파일을 정리한다. 작업 행은 생성 한도 집계를 위해
     * 생성 한도 집계를 위해 생성 후 최소 24시간 보존한다.
     */
    @Transactional
    public int cleanupExpiredFailedJobs() {
        Duration minimumRetention = Duration.ofHours(24);
        Duration retention = properties.failedAudioRetention().compareTo(minimumRetention) < 0
                ? minimumRetention
                : properties.failedAudioRetention();
        LocalDateTime retentionCutoff = LocalDateTime.now().minus(retention);
        List<SttJob> expiredJobs = sttJobRepository.findExpiredFailedJobs(
                SttStatus.FAILED,
                retentionCutoff,
                PageRequest.of(0, 100)
        );
        if (expiredJobs.isEmpty()) {
            return 0;
        }

        List<String> audioPaths = expiredJobs.stream().map(SttJob::getAudioPath).toList();
        sttJobRepository.deleteAll(expiredJobs);
        audioPaths.forEach(this::deleteAfterCommit);
        log.info("보관 기간이 지난 실패 STT 작업을 정리했습니다. count={}", expiredJobs.size());
        return expiredJobs.size();
    }

    private SttJob findJob(Long babyId, Long jobId) {
        return sttJobRepository.findByIdAndBabyId(jobId, babyId)
                .orElseThrow(SttJobNotFoundException::new);
    }

    private boolean isCurrentAttempt(SttJob job, String claimToken) {
        return job.getStatus() == SttStatus.PROCESSING
                && claimToken != null
                && claimToken.equals(job.getClaimToken());
    }

    private void ensureCapacity(Long userId, Long babyId) {
        if (sttJobRepository.countCreatedByUserSince(userId, LocalDateTime.now().minusHours(24))
                >= MAX_JOBS_PER_USER_PER_DAY) {
            throw new SttJobLimitExceededException();
        }
        if (sttJobRepository.countByBabyIdAndStatusIn(babyId, ACTIVE_JOB_STATUSES)
                >= MAX_ACTIVE_JOBS_PER_BABY) {
            throw new SttJobLimitExceededException();
        }
    }

    private void requireOwner(SttJob job, Long memberId) {
        if (!job.getFamilyMemberId().equals(memberId)) {
            throw new AccessDeniedException("음성 변환 작업을 처리할 권한이 없습니다.");
        }
    }

    private void deleteAfterCommit(String audioPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(audioPath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(audioPath);
            }
        });
    }

    private void deleteQuietly(String audioPath) {
        try {
            audioStorage.delete(audioPath);
        } catch (RuntimeException e) {
            log.warn("음성 파일을 삭제하지 못했습니다. path={}", audioPath, e);
        }
    }
}
