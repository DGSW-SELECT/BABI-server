package com.select.domain.care.service;

import com.select.domain.care.analyzer.CareRecordAnalyzer;
import com.select.domain.care.analyzer.CareRecordDraft;
import com.select.domain.care.dto.request.CreateCareRecordRequest;
import com.select.domain.care.entity.CareRecordType;
import com.select.domain.care.entity.DiaperType;
import com.select.domain.care.entity.FeedingType;
import com.select.domain.care.repository.CareRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareRecordWriterTest {

    @Mock
    private CareRecordRepository careRecordRepository;

    @Mock
    private CareRecordAnalyzer careRecordAnalyzer;

    @InjectMocks
    private CareRecordWriter careRecordWriter;

    @Test
    void mapsSleepDraftToRequestWithCalculatedEndTimeAndVoiceInput() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 3, 10, 0);
        when(careRecordAnalyzer.analyze("2시간 잤어요", start)).thenReturn(List.of(
                new CareRecordDraft(CareRecordType.SLEEP, start, null, 120, null, null, "2시간 잤어요")
        ));

        careRecordWriter.analyzeAndSave(1L, 2L, 3L, "2시간 잤어요", start);

        ArgumentCaptor<CreateCareRecordRequest> captor = ArgumentCaptor.forClass(CreateCareRecordRequest.class);
        verify(careRecordRepository).save(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(2L), captor.capture());
        assertThat(captor.getValue().recordType()).isEqualTo(CareRecordType.SLEEP);
        assertThat(captor.getValue().occurredAt()).isEqualTo(start);
        assertThat(captor.getValue().sleepEndAt()).isEqualTo(start.plusMinutes(120));
        assertThat(captor.getValue().voiceInputId()).isEqualTo(3L);
        assertThat(captor.getValue().note()).isEqualTo("2시간 잤어요");
    }

    @Test
    void truncatesLongMemoToRequestLimit() {
        String memo = "가".repeat(5100);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 3, 10, 0);
        when(careRecordAnalyzer.analyze(memo, occurredAt)).thenReturn(List.of(
                new CareRecordDraft(CareRecordType.FEEDING, occurredAt, 120, null,
                        FeedingType.FORMULA, null, memo)
        ));

        careRecordWriter.analyzeAndSave(1L, 2L, 3L, memo, occurredAt);

        ArgumentCaptor<CreateCareRecordRequest> captor = ArgumentCaptor.forClass(CreateCareRecordRequest.class);
        verify(careRecordRepository).save(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(2L), captor.capture());
        assertThat(captor.getValue().note()).hasSize(5000);
        assertThat(captor.getValue().amountMl()).isEqualTo(120);
        assertThat(captor.getValue().feedingType()).isEqualTo(FeedingType.FORMULA);
        assertThat(captor.getValue().voiceInputId()).isEqualTo(3L);
    }

    @Test
    void savesMultipleDraftsInAnalyzerOrder() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 3, 10, 0);
        when(careRecordAnalyzer.analyze("feeding and diaper", occurredAt)).thenReturn(List.of(
                new CareRecordDraft(CareRecordType.FEEDING, occurredAt, 120, null,
                        FeedingType.FORMULA, null, "feeding"),
                new CareRecordDraft(CareRecordType.DIAPER, occurredAt, null, null,
                        null, DiaperType.STOOL, "diaper")
        ));

        careRecordWriter.analyzeAndSave(1L, 2L, 3L, "feeding and diaper", occurredAt);

        ArgumentCaptor<CreateCareRecordRequest> captor = ArgumentCaptor.forClass(CreateCareRecordRequest.class);
        verify(careRecordRepository, org.mockito.Mockito.times(2)).save(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L), captor.capture());
        assertThat(captor.getAllValues()).extracting(CreateCareRecordRequest::recordType)
                .containsExactly(CareRecordType.FEEDING, CareRecordType.DIAPER);
        assertThat(captor.getAllValues()).allSatisfy(request -> assertThat(request.voiceInputId()).isEqualTo(3L));
    }

    @Test
    void skipsDraftThatWouldCreateSleepRecordLongerThanOneDay() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 3, 10, 0);
        when(careRecordAnalyzer.analyze("25시간 잤어요", occurredAt)).thenReturn(List.of(
                new CareRecordDraft(CareRecordType.SLEEP, occurredAt, null, 25 * 60, null, null, "25시간 잤어요")
        ));

        careRecordWriter.analyzeAndSave(1L, 2L, 3L, "25시간 잤어요", occurredAt);

        org.mockito.Mockito.verifyNoInteractions(careRecordRepository);
    }
}
