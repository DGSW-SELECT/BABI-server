package com.select.domain.parenting.repository;

import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecord;
import com.select.domain.parenting.entity.ParentingRecordType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ParentingRecordRepositoryTest {

    @Autowired
    private ParentingRecordRepository parentingRecordRepository;

    @Test
    void savesParentingRecordAndFindsItByBabyAndVoiceInput() {
        ParentingRecord saved = parentingRecordRepository.save(ParentingRecord.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .voiceInputId(10L)
                .recordType(ParentingRecordType.FEEDING)
                .occurredAt(LocalDateTime.of(2026, 11, 3, 14, 0))
                .amountMl(100)
                .feedingType(FeedingType.FORMULA)
                .memo("오후 2시에 분유 100ml 먹였어")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(parentingRecordRepository.findAllByBabyIdAndVoiceInputIdOrderByIdAsc(1L, 10L))
                .containsExactly(saved);
        assertThat(parentingRecordRepository.findAllByBabyIdAndVoiceInputIdOrderByIdAsc(2L, 10L))
                .isEmpty();
    }
}
