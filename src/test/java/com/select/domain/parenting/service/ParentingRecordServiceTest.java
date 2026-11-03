package com.select.domain.parenting.service;

import com.select.domain.parenting.analyzer.ParentingRecordAnalyzer;
import com.select.domain.parenting.analyzer.ParentingRecordDraft;
import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.DiaperType;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecord;
import com.select.domain.parenting.entity.ParentingRecordType;
import com.select.domain.parenting.repository.ParentingRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParentingRecordServiceTest {

    @Mock
    private ParentingRecordRepository parentingRecordRepository;

    @Mock
    private ParentingRecordAnalyzer parentingRecordAnalyzer;

    @InjectMocks
    private ParentingRecordService parentingRecordService;

    @Test
    @SuppressWarnings("unchecked")
    void analyzesTranscriptAndSavesParentingRecords() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 3, 20, 30);
        String transcript = "오후 2시에 분유 100ml 먹였어";
        ParentingRecordDraft draft = new ParentingRecordDraft(
                ParentingRecordType.FEEDING,
                LocalDateTime.of(2026, 11, 3, 14, 0),
                100,
                null,
                FeedingType.FORMULA,
                null,
                transcript
        );
        when(parentingRecordAnalyzer.analyze(transcript, createdAt))
                .thenReturn(List.of(draft));
        when(parentingRecordRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ParentingRecord> records = invocation.getArgument(0);
            ParentingRecord record = records.get(0);
            ReflectionTestUtils.setField(record, "id", 100L);
            ReflectionTestUtils.setField(record, "createdAt", createdAt);
            return records;
        });

        List<ParentingRecordResponse> responses = parentingRecordService.analyzeAndSave(
                1L, 7L, 10L, transcript, createdAt
        );

        ArgumentCaptor<List<ParentingRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(parentingRecordRepository).saveAll(captor.capture());
        ParentingRecord captured = captor.getValue().get(0);
        assertThat(captured.getBabyId()).isEqualTo(1L);
        assertThat(captured.getFamilyMemberId()).isEqualTo(7L);
        assertThat(captured.getVoiceInputId()).isEqualTo(10L);
        assertThat(captured.getRecordType()).isEqualTo(ParentingRecordType.FEEDING);
        assertThat(captured.getAmountMl()).isEqualTo(100);
        assertThat(captured.getFeedingType()).isEqualTo(FeedingType.FORMULA);

        assertThat(responses).hasSize(1);
        ParentingRecordResponse response = responses.get(0);
        assertThat(response.parentingRecordId()).isEqualTo(100L);
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.recordType()).isEqualTo(ParentingRecordType.FEEDING);
        assertThat(response.occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 0));
        assertThat(response.amountMl()).isEqualTo(100);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void doesNotSaveWhenAnalyzerReturnsNoRecord() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 3, 20, 30);
        String transcript = "오늘 기분이 좋아";
        when(parentingRecordAnalyzer.analyze(transcript, createdAt)).thenReturn(List.of());

        List<ParentingRecordResponse> responses = parentingRecordService.analyzeAndSave(
                1L, 7L, 10L, transcript, createdAt
        );

        assertThat(responses).isEmpty();
        verify(parentingRecordRepository, never()).saveAll(anyList());
    }

    @Test
    void findsRecordsByBabyAndVoiceInput() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 3, 20, 30);
        ParentingRecord record = ParentingRecord.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .voiceInputId(10L)
                .recordType(ParentingRecordType.DIAPER)
                .occurredAt(createdAt)
                .diaperType(DiaperType.STOOL)
                .memo("응가 했어")
                .build();
        ReflectionTestUtils.setField(record, "id", 100L);
        ReflectionTestUtils.setField(record, "createdAt", createdAt);
        when(parentingRecordRepository.findAllByBabyIdAndVoiceInputIdOrderByIdAsc(1L, 10L))
                .thenReturn(List.of(record));

        List<ParentingRecordResponse> responses = parentingRecordService.findByVoiceInput(1L, 10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).parentingRecordId()).isEqualTo(100L);
        assertThat(responses.get(0).recordType()).isEqualTo(ParentingRecordType.DIAPER);
        assertThat(responses.get(0).diaperType()).isEqualTo(DiaperType.STOOL);
    }
}
