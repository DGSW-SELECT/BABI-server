package com.select.domain.voice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 비동기 STT 작업.
 *
 * <p>음성 파일은 작업 제출 시 저장소에 보관하고, 워커가 상태를
 * {@code PENDING -> PROCESSING -> COMPLETED/FAILED}로 전이시키며 처리한다.
 * 성공하면 {@link VoiceInput}과 육아기록을 생성하고 작업에는 voiceInputId만 남긴다.</p>
 */
@Entity
@Getter
@Table(
        name = "stt_jobs",
        indexes = {
                @Index(name = "idx_stt_jobs_baby_id", columnList = "baby_id"),
                @Index(name = "idx_stt_jobs_status_next_attempt_at", columnList = "status,next_attempt_at")
        }
)
public class SttJob {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "family_member_id", nullable = false)
    private Long familyMemberId;

    @Column(name = "audio_path", nullable = false, length = 500)
    private String audioPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SttStatus status;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "voice_input_id")
    private Long voiceInputId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected SttJob() {
    }

    @Builder
    public SttJob(
            Long babyId,
            Long familyMemberId,
            String audioPath,
            SttStatus status,
            String transcript,
            Long voiceInputId,
            int attemptCount,
            String lastError,
            LocalDateTime nextAttemptAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
        this.babyId = babyId;
        this.familyMemberId = familyMemberId;
        this.audioPath = audioPath;
        this.status = status;
        this.transcript = transcript;
        this.voiceInputId = voiceInputId;
        this.attemptCount = attemptCount;
        this.lastError = lastError;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public static SttJob pending(
            Long babyId,
            Long familyMemberId,
            String audioPath,
            LocalDateTime now
    ) {
        return SttJob.builder()
                .babyId(babyId)
                .familyMemberId(familyMemberId)
                .audioPath(audioPath)
                .status(SttStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void complete(String transcript, Long voiceInputId, LocalDateTime now) {
        this.status = SttStatus.COMPLETED;
        this.transcript = transcript;
        this.voiceInputId = voiceInputId;
        this.lastError = null;
        this.nextAttemptAt = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    /**
     * 실패를 기록한다.
     *
     * @param nextAttemptAt 재시도 예정 시각. {@code null}이면 최종 실패로 처리한다.
     */
    public void fail(String error, LocalDateTime nextAttemptAt, LocalDateTime now) {
        this.lastError = truncate(error);
        this.updatedAt = now;
        if (nextAttemptAt == null) {
            this.status = SttStatus.FAILED;
            this.nextAttemptAt = null;
            this.completedAt = now;
            return;
        }

        this.status = SttStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.startedAt = null;
        this.completedAt = null;
    }

    /**
     * 사용자가 수동으로 재시도할 때 작업을 초기 상태로 되돌린다.
     */
    public void resetForRetry(LocalDateTime now) {
        this.status = SttStatus.PENDING;
        this.attemptCount = 0;
        this.lastError = null;
        this.transcript = null;
        this.voiceInputId = null;
        this.nextAttemptAt = now;
        this.startedAt = null;
        this.completedAt = null;
        this.updatedAt = now;
    }

    /**
     * 처리 중 멈춘 작업을 복구한다.
     *
     * @param retryable 남은 재시도 횟수가 있으면 {@code true}
     */
    public void recoverStale(String error, boolean retryable, LocalDateTime now) {
        fail(error, retryable ? now : null, now);
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
