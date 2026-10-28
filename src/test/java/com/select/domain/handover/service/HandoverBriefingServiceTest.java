package com.select.domain.handover.service;

import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.entity.HandoverBriefing;
import com.select.domain.handover.entity.HandoverMemo;
import com.select.domain.handover.exception.HandoverBriefingNotFoundException;
import com.select.domain.handover.exception.HandoverBriefingGenerationException;
import com.select.domain.handover.exception.NoDailyCareRecordException;
import com.select.domain.handover.repository.HandoverBriefingRepository;
import com.select.domain.handover.repository.HandoverMemoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandoverBriefingServiceTest {

    private HandoverBriefingRepository handoverBriefingRepository;
    private DailyCareRecordProvider dailyCareRecordProvider;
    private HandoverBriefingGenerator handoverBriefingGenerator;
    private HandoverMemoRepository handoverMemoRepository;
    private HandoverBriefingService handoverBriefingService;

    @BeforeEach
    void setUp() {
        handoverBriefingRepository = mock(HandoverBriefingRepository.class);
        dailyCareRecordProvider = mock(DailyCareRecordProvider.class);
        handoverBriefingGenerator = mock(HandoverBriefingGenerator.class);
        handoverMemoRepository = mock(HandoverMemoRepository.class);
        handoverBriefingService = new HandoverBriefingService(
                handoverBriefingRepository,
                dailyCareRecordProvider,
                handoverBriefingGenerator,
                handoverMemoRepository
        );
    }

    @Test
    void createsAndSavesBriefingFromTodayRecords() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<String> records = List.of("수유 120ml", "낮잠 1시간");
        HandoverBriefing savedBriefing = new HandoverBriefing(
                1L,
                2L,
                today,
                "오늘 수유와 낮잠을 정리했습니다.",
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        );

        when(dailyCareRecordProvider.findToday(2L, today)).thenReturn(records);
        when(handoverMemoRepository
                .findByGroupIdAndBabyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        1L,
                        2L,
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                ))
                .thenReturn(List.of(new HandoverMemo(
                        1L,
                        2L,
                        3L,
                        "보호자 메모",
                        LocalDateTime.of(2026, 10, 27, 12, 0)
                )));
        when(handoverBriefingGenerator.generate(
                2L,
                today,
                records,
                List.of("보호자 메모")
        ))
                .thenReturn("오늘 수유와 낮잠을 정리했습니다.");
        when(handoverBriefingRepository.save(any(HandoverBriefing.class)))
                .thenReturn(savedBriefing);

        HandoverBriefingResponse response =
                handoverBriefingService.createBriefing(1L, 2L);

        assertEquals(today, response.briefingDate());
        assertEquals("오늘 수유와 낮잠을 정리했습니다.", response.summary());
        verify(handoverBriefingRepository).save(any(HandoverBriefing.class));
    }

    @Test
    void doesNotCallAiWhenThereAreNoTodayRecords() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(dailyCareRecordProvider.findToday(2L, today)).thenReturn(List.of());

        assertThrows(
                NoDailyCareRecordException.class,
                () -> handoverBriefingService.createBriefing(1L, 2L)
        );
    }

    @Test
    void convertsAiFailureToRetryableApplicationException() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<String> records = List.of("수유 120ml");
        when(dailyCareRecordProvider.findToday(2L, today)).thenReturn(records);
        when(handoverMemoRepository
                .findByGroupIdAndBabyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        1L,
                        2L,
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                ))
                .thenReturn(List.of());
        when(handoverBriefingGenerator.generate(2L, today, records, List.of()))
                .thenThrow(new IllegalStateException("AI provider failed"));

        assertThrows(
                HandoverBriefingGenerationException.class,
                () -> handoverBriefingService.createBriefing(1L, 2L)
        );
        verify(handoverBriefingRepository, never()).save(any(HandoverBriefing.class));
    }

    @Test
    void returnsLatestSavedBriefing() {
        HandoverBriefing savedBriefing = new HandoverBriefing(
                1L,
                2L,
                LocalDate.of(2026, 10, 27),
                "저장된 브리핑",
                LocalDateTime.of(2026, 10, 27, 18, 0)
        );
        when(handoverBriefingRepository
                .findTopByGroupIdAndBabyIdOrderByCreatedAtDesc(1L, 2L))
                .thenReturn(Optional.of(savedBriefing));

        HandoverBriefingResponse response =
                handoverBriefingService.getLatestBriefing(1L, 2L);

        assertEquals(LocalDate.of(2026, 10, 27), response.briefingDate());
        assertEquals("저장된 브리핑", response.summary());
    }

    @Test
    void throwsWhenLatestBriefingDoesNotExist() {
        when(handoverBriefingRepository
                .findTopByGroupIdAndBabyIdOrderByCreatedAtDesc(1L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(
                HandoverBriefingNotFoundException.class,
                () -> handoverBriefingService.getLatestBriefing(1L, 2L)
        );
    }
}
