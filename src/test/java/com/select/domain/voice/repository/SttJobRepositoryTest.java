package com.select.domain.voice.repository;

import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SttJobRepositoryTest {

    @Autowired
    private SttJobRepository sttJobRepository;

    @Test
    void claimMovesPendingJobToProcessingExactlyOnce() {
        LocalDateTime now = LocalDateTime.now();
        SttJob job = sttJobRepository.saveAndFlush(SttJob.pending(1L, 7L, "2026/11/03/audio.mp3", now));

        int firstClaim = sttJobRepository.claim(
                job.getId(), SttStatus.PROCESSING, SttStatus.PENDING, now
        );
        int secondClaim = sttJobRepository.claim(
                job.getId(), SttStatus.PROCESSING, SttStatus.PENDING, now
        );

        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
        SttJob claimed = sttJobRepository.findById(job.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(SttStatus.PROCESSING);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);
        assertThat(claimed.getStartedAt()).isEqualTo(now);
    }

    @Test
    void findReadyIdsReturnsOnlyDuePendingJobs() {
        LocalDateTime now = LocalDateTime.now();
        SttJob due = sttJobRepository.saveAndFlush(
                SttJob.pending(1L, 7L, "2026/11/03/due.mp3", now.minusMinutes(1))
        );
        SttJob future = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .audioPath("2026/11/03/future.mp3")
                .status(SttStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now.plusMinutes(10))
                .createdAt(now)
                .updatedAt(now)
                .build());
        SttJob processing = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(1L)
                .familyMemberId(7L)
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
        LocalDateTime now = LocalDateTime.now();
        sttJobRepository.saveAndFlush(SttJob.pending(1L, 7L, "2026/11/03/first.mp3", now));
        sttJobRepository.saveAndFlush(SttJob.pending(1L, 7L, "2026/11/03/second.mp3", now));

        List<Long> readyIds = sttJobRepository.findReadyIds(
                SttStatus.PENDING, now, PageRequest.of(0, 1)
        );

        assertThat(readyIds).hasSize(1);
    }

    @Test
    void findsStaleProcessingJobs() {
        LocalDateTime now = LocalDateTime.now();
        SttJob stale = sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .audioPath("2026/11/03/stale.mp3")
                .status(SttStatus.PROCESSING)
                .attemptCount(1)
                .startedAt(now.minusMinutes(30))
                .createdAt(now.minusMinutes(30))
                .updatedAt(now.minusMinutes(30))
                .build());
        sttJobRepository.saveAndFlush(SttJob.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .audioPath("2026/11/03/active.mp3")
                .status(SttStatus.PROCESSING)
                .attemptCount(1)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

        List<SttJob> staleJobs = sttJobRepository.findAllByStatusAndStartedAtBefore(
                SttStatus.PROCESSING, now.minusMinutes(10)
        );

        assertThat(staleJobs).extracting(SttJob::getId).containsExactly(stale.getId());
    }
}
