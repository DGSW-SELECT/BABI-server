package com.select.domain.handover.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.entity.HandoverBriefing;
import com.select.domain.handover.exception.HandoverBriefingGenerationException;
import com.select.domain.handover.exception.HandoverBriefingNotFoundException;
import com.select.domain.handover.exception.NoDailyCareRecordException;
import com.select.domain.handover.repository.HandoverBriefingRepository;
import com.select.domain.report.dto.response.DailyReportFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HandoverBriefingServiceTest {

    private HandoverBriefingRepository briefings;
    private DailyCareRecordProvider records;
    private HandoverBriefingGenerator generator;
    private BabyAccessRepository access;
    private BabyRepository babies;
    private HandoverBriefingRateLimiter rateLimiter;
    private PlatformTransactionManager transactionManager;
    private HandoverBriefingService service;

    @BeforeEach
    void setUp() {
        briefings = mock(HandoverBriefingRepository.class);
        records = mock(DailyCareRecordProvider.class);
        generator = mock(HandoverBriefingGenerator.class);
        access = mock(BabyAccessRepository.class);
        babies = mock(BabyRepository.class);
        rateLimiter = mock(HandoverBriefingRateLimiter.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(access.findMemberId(1L, 2L, "CARE_WRITE", true)).thenReturn(Optional.of(10L));
        when(access.findMemberId(1L, 2L, "CARE_READ", false)).thenReturn(Optional.of(10L));
        when(babies.findByIdForUpdate(2L)).thenReturn(Optional.of(mock(Baby.class)));
        service = new HandoverBriefingService(
                briefings, records, generator, access, babies, rateLimiter, transactionManager);
    }

    @Test
    void createsAndSavesBriefingFromAnonymousTodayFacts() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        DailyReportFacts facts = facts(today);
        HandoverBriefing saved = briefing(today, "오늘 수유와 낮잠을 정리했습니다.");
        when(records.findToday(2L, today)).thenReturn(facts);
        when(generator.generate(facts)).thenReturn("오늘 수유와 낮잠을 정리했습니다.");
        when(briefings.save(any(HandoverBriefing.class))).thenReturn(saved);

        HandoverBriefingResponse response = service.createBriefing(1L, 2L);

        assertEquals(today, response.briefingDate());
        assertEquals("오늘 수유와 낮잠을 정리했습니다.", response.summary());
        verify(briefings).save(any(HandoverBriefing.class));
        InOrder order = inOrder(generator, transactionManager);
        order.verify(generator).generate(facts);
        order.verify(transactionManager).getTransaction(any());
    }

    @Test
    void doesNotCallAiWhenThereAreNoTodayRecords() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(records.findToday(2L, today)).thenReturn(emptyFacts(today));

        assertThrows(NoDailyCareRecordException.class, () -> service.createBriefing(1L, 2L));

        verifyNoInteractions(generator, briefings);
    }

    @Test
    void convertsAiFailureToRetryableApplicationException() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        DailyReportFacts facts = facts(today);
        when(records.findToday(2L, today)).thenReturn(facts);
        when(generator.generate(facts)).thenThrow(new IllegalStateException("provider details"));

        HandoverBriefingGenerationException exception = assertThrows(
                HandoverBriefingGenerationException.class, () -> service.createBriefing(1L, 2L));

        verify(briefings, never()).save(any());
        verifyNoInteractions(transactionManager);
        assertFalse(exception.getMessage().contains("provider details"));
    }

    @Test
    void rechecksWritePermissionAfterAiAndDoesNotSaveWhenMembershipWasRevoked() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        DailyReportFacts facts = facts(today);
        when(access.findMemberId(1L, 2L, "CARE_WRITE", true))
                .thenReturn(Optional.of(10L), Optional.empty());
        when(records.findToday(2L, today)).thenReturn(facts);
        when(generator.generate(facts)).thenReturn("생성된 브리핑");

        assertThrows(AccessDeniedException.class, () -> service.createBriefing(1L, 2L));

        verify(briefings, never()).save(any());
    }

    @Test
    void returnsLatestSavedBriefingToReader() {
        HandoverBriefing saved = briefing(LocalDate.of(2026, 10, 27), "저장된 브리핑");
        when(briefings.findTopByBabyIdOrderByGeneratedAtDescIdDesc(2L)).thenReturn(Optional.of(saved));

        HandoverBriefingResponse response = service.getLatestBriefing(1L, 2L);

        assertEquals(LocalDate.of(2026, 10, 27), response.briefingDate());
        assertEquals("저장된 브리핑", response.summary());
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void throwsWhenLatestBriefingDoesNotExist() {
        when(briefings.findTopByBabyIdOrderByGeneratedAtDescIdDesc(2L)).thenReturn(Optional.empty());

        assertThrows(HandoverBriefingNotFoundException.class,
                () -> service.getLatestBriefing(1L, 2L));
    }

    @Test
    void deniesUserWithoutActionPermissionBeforeUsingRecordsOrAi() {
        assertThrows(AccessDeniedException.class, () -> service.createBriefing(99L, 2L));
        assertThrows(AccessDeniedException.class, () -> service.getLatestBriefing(99L, 2L));

        verifyNoInteractions(rateLimiter, records, generator, briefings);
    }

    @Test
    void rateLimitStopsRecordLookupGenerationAndStorage() {
        org.mockito.Mockito.doThrow(new com.select.domain.handover.exception.HandoverBriefingRateLimitedException(60))
                .when(rateLimiter).check(2L);

        assertThrows(com.select.domain.handover.exception.HandoverBriefingRateLimitedException.class,
                () -> service.createBriefing(1L, 2L));

        verifyNoInteractions(records, generator, briefings);
    }

    private DailyReportFacts facts(LocalDate date) {
        return new DailyReportFacts(date, 2, 2, 240, 1, 0, 60,
                1, 36.7, 36.7, 36.7, 1, 0);
    }

    private DailyReportFacts emptyFacts(LocalDate date) {
        return new DailyReportFacts(date, 0, 0, 0, 0, 0, 0,
                0, null, null, null, 0, 0);
    }

    private HandoverBriefing briefing(LocalDate date, String summary) {
        return new HandoverBriefing(date.atStartOfDay(), date.atTime(18, 0), "COMPLETED", 2L,
                date, summary, LocalDateTime.of(date, java.time.LocalTime.of(18, 0)));
    }
}
