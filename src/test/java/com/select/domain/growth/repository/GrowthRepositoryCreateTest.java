package com.select.domain.growth.repository;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.entity.GrowthRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop"
}) // 계속 오류가 터져서 schema-test.sql 코드 신경 안쓰고 바로 테스트 하는 방식으로 변경
class GrowthRepositoryCreateTest {
    @Autowired
    private GrowthRepository growthRepository;

    @Autowired
    private BabyRepository babyRepository;

    private Baby createBaby(String name) {
        return Baby.builder()
                .name(name)
                .birthDate(LocalDate.of(2025, 1, 1))
                .createdByUserId(1L)
                .build();
    }

    private Baby baby1;
    private Baby baby2;


    private final LocalDate measuredDate = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        // 아기 데이터 생성
        baby1 = babyRepository.save(createBaby("테스트 아기 1"));
        baby2 = babyRepository.save(createBaby("테스트 아기 2"));
    }

    @Test
    void existsByBabyIdAndMeasuredDate_returnTrue() {
        // given
        growthRepository.save(createGrowthRecord(baby1, measuredDate));

        // when
        boolean result = growthRepository.existsByBaby_IdAndMeasuredDate(
                baby1.getId(),
                measuredDate
        );

        // then
        assertThat(result).isTrue();
    }

    @Test
    void existsByBabyIdAndDifferentDate_returnFalse() {
        // given
        growthRepository.save(createGrowthRecord(baby1, measuredDate));

        // when
        boolean result = growthRepository.existsByBaby_IdAndMeasuredDate(
                baby1.getId(),
                measuredDate.plusDays(1)
        );

        // then
        assertThat(result).isFalse();
    }

    @Test
    void existsByDifferentBabyIdAndSameDate_returnFalse() {
        // given
        growthRepository.save(createGrowthRecord(baby1, measuredDate));

        // when
        boolean result = growthRepository.existsByBaby_IdAndMeasuredDate(
                baby2.getId(),
                measuredDate
        );

        // then
        assertThat(result).isFalse();
    }

    private GrowthRecord createGrowthRecord(Baby baby, LocalDate date) {
        return new GrowthRecord(
                baby,
                1L,
                new BigDecimal("85.50"),
                new BigDecimal("12.30"),
                date,
                "테스트 기록"
        );
    }
}