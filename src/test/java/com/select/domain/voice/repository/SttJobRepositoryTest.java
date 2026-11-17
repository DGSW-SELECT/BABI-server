package com.select.domain.voice.repository;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SttJobRepositoryTest {

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BabyRepository babyRepository;

    @Autowired
    private BabyMemberRepository babyMemberRepository;

    private Long babyId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("stt-repository@example.invalid")
                .passwordHash("test-password-hash")
                .nickname("테스트 보호자")
                .build());
        Baby baby = babyRepository.save(Baby.builder()
                .name("테스트 아기")
                .birthDate(LocalDate.of(2026, 1, 1))
                .createdByUserId(user.getId())
                .build());
        BabyMember member = babyMemberRepository.save(BabyMember.builder()
                .babyId(baby.getId())
                .userId(user.getId())
                .role(BabyRole.OWNER)
                .build());
        babyId = baby.getId();
        memberId = member.getId();
    }

    @Test
    void claimMovesPendingJobToProcessingExactlyOnce() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SttJob job = sttJobRepository.saveAndFlush(
                SttJob.pending(babyId, memberId, "2026/11/03/audio.mp3", now)
        );

        int firstClaim = sttJobRepository.claim(
                job.getId(), SttStatus.PROCESSING, SttStatus.PENDING, now, "claim-one"
        );
        int secondClaim = sttJobRepository.claim(
                job.getId(), SttStatus.PROCESSING, SttStatus.PENDING, now, "claim-two"
        );

        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
        SttJob claimed = sttJobRepository.findById(job.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(SttStatus.PROCESSING);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);
        assertThat(claimed.getClaimToken()).isEqualTo("claim-one");
        assertThat(claimed.getStartedAt()).isEqualTo(now);
    }

    @Test
    void findReadyIdsReturnsOnlyDuePendingJobs() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SttJob due = sttJobRepository.saveAndFlush(
                SttJob.pending(babyId, memberId, "2026/11/03/due.mp3", now.minusMinutes(1))
        );
        SttJob future = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(babyId)
                .familyMemberId(memberId)
                .audioPath("2026/11/03/future.mp3")
                .status(SttStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now.plusMinutes(10))
                .createdAt(now)
                .updatedAt(now)
                .build());
        SttJob processing = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(babyId)
                .familyMemberId(memberId)
                .audioPath("2026/11/03/processing.mp3")
                .status(SttStatus.PROCESSING)
                .attemptCount(1)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

        List<Long> readyIds = sttJobRepository.findReadyIds(
                SttStatus.PENDING, now, PageRequest.of(0, 10)
        );

        assertThat(readyIds).containsExactly(due.getId());
        assertThat(readyIds).doesNotContain(future.getId(), processing.getId());
    }

    @Test
    void findReadyIdsRespectsPageSize() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        sttJobRepository.saveAndFlush(SttJob.pending(babyId, memberId, "2026/11/03/first.mp3", now));
        sttJobRepository.saveAndFlush(SttJob.pending(babyId, memberId, "2026/11/03/second.mp3", now));

        List<Long> readyIds = sttJobRepository.findReadyIds(
                SttStatus.PENDING, now, PageRequest.of(0, 1)
        );

        assertThat(readyIds).hasSize(1);
    }

    @Test
    void countsJobsSubmittedByTheUserAcrossMemberships() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        sttJobRepository.saveAndFlush(
                SttJob.pending(babyId, memberId, "2026/11/03/audio.mp3", now)
        );

        long userId = babyRepository.findById(babyId).orElseThrow().getCreatedByUserId();
        long count = sttJobRepository.countCreatedByUserSince(userId, now.minusHours(24));

        assertThat(count).isEqualTo(1);
    }

    @Test
    void findsStaleProcessingJobs() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SttJob stale = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(babyId)
                .familyMemberId(memberId)
                .audioPath("2026/11/03/stale.mp3")
                .status(SttStatus.PROCESSING)
                .attemptCount(1)
                .startedAt(now.minusMinutes(30))
                .createdAt(now.minusMinutes(30))
                .updatedAt(now.minusMinutes(30))
                .build());
        sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(babyId)
                .familyMemberId(memberId)
                .audioPath("2026/11/03/active.mp3")
                .status(SttStatus.PROCESSING)
                .attemptCount(1)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

        List<SttJob> staleJobs = sttJobRepository.findStaleJobsForUpdate(
                SttStatus.PROCESSING, now.minusMinutes(10)
        );

        assertThat(staleJobs).extracting(SttJob::getId).containsExactly(stale.getId());
    }

    @Test
    void findsOnlyExpiredFinalFailuresForCleanup() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SttJob expired = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(babyId)
                .familyMemberId(memberId)
                .audioPath("2026/11/03/expired.mp3")
                .status(SttStatus.FAILED)
                .attemptCount(3)
                .completedAt(now.minusDays(2))
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusDays(2))
                .build());
        SttJob recent = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(babyId)
                .familyMemberId(memberId)
                .audioPath("2026/11/03/recent.mp3")
                .status(SttStatus.FAILED)
                .attemptCount(3)
                .completedAt(now.minusHours(1))
                .createdAt(now.minusHours(1))
                .updatedAt(now.minusHours(1))
                .build());

        List<SttJob> expiredJobs = sttJobRepository.findExpiredFailedJobs(
                SttStatus.FAILED, now.minusDays(1), PageRequest.of(0, 100)
        );

        assertThat(expiredJobs).extracting(SttJob::getId).containsExactly(expired.getId());
        assertThat(expiredJobs).extracting(SttJob::getId).doesNotContain(recent.getId());
    }
}
