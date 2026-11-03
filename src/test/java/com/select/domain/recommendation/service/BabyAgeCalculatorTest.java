package com.select.domain.recommendation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BabyAgeCalculatorTest {

    private BabyAgeCalculator babyAgeCalculator;

    @BeforeEach
    void setUp() {
        babyAgeCalculator = new BabyAgeCalculator();
    }

    @Test
    void 생후_6개월을_계산한다() {
        LocalDate birthDate = LocalDate.of(2026, 1, 1);
        LocalDate baseDate = LocalDate.of(2026, 7, 1);

        long actualMonths = babyAgeCalculator.calculateMonths(birthDate, baseDate);

        assertEquals(6, actualMonths);
    }

    @Test
    void 생일이_아직_지나지_않았으면_개월수를_하나_줄인다() {
        LocalDate birthDate = LocalDate.of(2026, 1, 15);
        LocalDate baseDate = LocalDate.of(2026, 7, 10);

        long actualMonths = babyAgeCalculator.calculateMonths(birthDate, baseDate);

        assertEquals(5, actualMonths);
    }

    @Test
    void 생년월일이_기준일보다_미래면_예외가_발생한다() {
        LocalDate birthDate = LocalDate.of(2027, 1, 1);
        LocalDate baseDate = LocalDate.of(2026, 7, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> babyAgeCalculator.calculateMonths(birthDate, baseDate)
        );
    }
}
