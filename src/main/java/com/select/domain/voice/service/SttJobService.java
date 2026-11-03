package com.select.domain.voice.service;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.service.ParentingRecordService;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.AudioStorageException;
import com.select.domain.voice.exception.SttJobNotFoundException;
import com.select.domain.voice.exception.SttJobNotRetryableException;
import com.select.domain.voice.repository.SttJobRepository;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.storage.AudioStorage;
import com.select.domain.voice.support.AudioFileSupport;
import com.select.global.config.SttJobProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 비동기 STT 작업의 제출·조회·재시도와 상태 전이를 담당한다.
 *
 * <p>실제 STT 호출은 {@link SttJobWorker}가 맡고, 이 서비스는 DB 트랜잭션 경계를
 * 보장한다. STT 호출 동안 트랜잭션을 열어 두지 않기 위해 처리 흐름을 분리했다.</p>
 */
@Service
public class SttJobService {

    private static final Logger log = LoggerFactory.getLogger(SttJobService.class);

    private final SttJobRepository sttJobRepository;
    private final VoiceInputRepository voiceInputRepository;
    private final ParentingRecordService parentingRecordService;
    private final AudioStorage audioStorage;
    private final SttJobProperties properties;

    public SttJobService(
            SttJobRepository sttJobRepository,
            VoiceInputRepository voiceInputRepository,
            ParentingRecordService parentingRecordService,
            AudioStorage audioStorage,
            SttJobProperties properties
    ) {
        this.sttJobRepository = sttJobRepository;
        this.voiceInputRepository = voiceInputRepository;
        this.parentingRecordService = parentingRecordService;
        this.audioStorage = audioStorage;
        this.properties = properties;
    }

    /**
     * 음성 파일을 저장하고 STT 작업을 {@code PENDING} 상태로 등록한다.
     */
    @Transactional
    public SttJobResponse submit(Long babyId, Long familyMemberId, MultipartFile audio) {
        AudioFileSupport.validate(audio);
        String audioPath = audioStorage.store(audio);

        try {
            SttJob job = sttJobRepository.saveAndFlush(SttJob.pending(
                    babyId,
                    familyMemberId,
                    audioPath,
                    LocalDateTime.now()
            ));
            log.info("STT 작업이 등록되었습니다. jobId={}, babyId={}", job.getId(), babyId);
            return SttJobResponse.of(job, properties.maxAttempts());
        } catch (RuntimeException e) {
            deleteQuietly(audioPath);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public SttJobResponse get(Long babyId, Long jobId) {
        SttJob job = findJob(babyId, jobId);
        return SttJobResponse.of(job, properties.maxAttempts(), findParentingRecords(job));
    }

    /**
     * 최종 실패한 작업을 사용자가 다시 시도할 수 있게 한다.
     */
    @Transactional
    public SttJobResponse retry(Long babyId, Long jobId) {
        SttJob job = findJob(babyId, jobId);
        if (job.getStatus() != SttStatus.FAILED) {
            throw new SttJobNotRetryableException("실패한 음성 변환 작업만 재시도할 수 있습니다.");
        }
        if (!audioStorage.exists(job.getAudioPath())) {
            throw new AudioStorageException("재시도할 음성 파일을 찾을 수 없습니다.");
        }

        job.resetForRetry(LocalDateTime.now());
        log.info("STT 작업을 재시도합니다. jobId={}", jobId);
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
     * 작업을 원자적으로 선점한다. 다른 인스턴스가 먼저 가져갔으면 {@code false}를 반환한다.
     */
    @Transactional
    public boolean claim(Long jobId) {
        return sttJobRepository.claim(
                jobId,
                SttStatus.PROCESSING,
                SttStatus.PENDING,
                LocalDateTime.now()
        ) == 1;
    }

    @Transactional(readOnly = true)
    public SttJob findForProcessing(Long jobId) {
        return sttJobRepository.findById(jobId).orElse(null);
    }

    /**
     * STT 성공 결과를 음성 기록과 육아기록으로 저장하고 작업을 완료 처리한다.
     */
    @Transactional
    public SttJobResponse complete(Long jobId, String transcript) {
        SttJob job = sttJobRepository.findById(jobId)
                .orElseThrow(SttJobNotFoundException::new);
        if (job.getStatus() != SttStatus.PROCESSING) {
            log.warn("처리 중 상태가 아니어서 완료 처리를 건너뜁니다. jobId={}, status={}",
                    jobId, job.getStatus());
            return SttJobResponse.of(job, properties.maxAttempts());
        }

        String trimmedTranscript = transcript.trim();
        LocalDateTime now = LocalDateTime.now();
        VoiceInput voiceInput = voiceInputRepository.save(VoiceInput.builder()
                .babyId(job.getBabyId())
                .familyMemberId(job.getFamilyMemberId())
                .transcript(trimmedTranscript)
                .sttStatus(SttStatus.COMPLETED)
                .build());

        LocalDateTime referenceTime = voiceInput.getCreatedAt() == null
                ? now
                : voiceInput.getCreatedAt();
        List<ParentingRecordResponse> parentingRecords = parentingRecordService.analyzeAndSave(
                job.getBabyId(),
                job.getFamilyMemberId(),
                voiceInput.getId(),
                trimmedTranscript,
                referenceTime
        );

        job.complete(trimmedTranscript, voiceInput.getId(), now);
        deleteAfterCommit(job.getAudioPath());
        log.info("STT 작업이 완료되었습니다. jobId={}, voiceInputId={}", jobId, voiceInput.getId());
        return SttJobResponse.of(job, properties.maxAttempts(), parentingRecords);
    }

    /**
     * STT 실패를 기록하고 남은 재시도 횟수에 따라 {@code PENDING} 또는 {@code FAILED}로 전이한다.
     */
    @Transactional
    public SttJobResponse fail(Long jobId, String error) {
        SttJob job = sttJobRepository.findById(jobId)
                .orElseThrow(SttJobNotFoundException::new);
        if (job.getStatus() != SttStatus.PROCESSING) {
            log.warn("처리 중 상태가 아니어서 실패 처리를 건너뜁니다. jobId={}, status={}",
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
        List<SttJob> staleJobs = sttJobRepository.findAllByStatusAndStartedAtBefore(
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

    private SttJob findJob(Long babyId, Long jobId) {
        return sttJobRepository.findByIdAndBabyId(jobId, babyId)
                .orElseThrow(SttJobNotFoundException::new);
    }

    private List<ParentingRecordResponse> findParentingRecords(SttJob job) {
        if (job.getStatus() != SttStatus.COMPLETED || job.getVoiceInputId() == null) {
            return List.of();
        }
        return parentingRecordService.findByVoiceInput(job.getBabyId(), job.getVoiceInputId());
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
