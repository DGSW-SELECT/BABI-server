package com.select.domain.growth.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.repository.GrowthRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GrowthServiceImplRequestTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private GrowthRecordCreateRequest request;
    private BabyMember member;
    private Baby baby;

    @BeforeEach
    void setUp() {
        request = new GrowthRecordCreateRequest(
                LocalDate.now(),
                new BigDecimal("72.50"),
                new BigDecimal("9.25"),
                "정기 성장 측정"
        );
        member = mock(BabyMember.class);
        baby = mock(Baby.class);
    }
    @Test
    void createGrowth_fail_whenHeightIsNegative() {
        // given
        GrowthRecordCreateRequest request = new GrowthRecordCreateRequest(
                LocalDate.of(2026, 11, 9),
                new BigDecimal("-10.00"),
                new BigDecimal("12.30"),
                "키 음수 테스트"
        );

        // when
        Set<ConstraintViolation<GrowthRecordCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("heightCm");
    }
    @Test
    void createGrowth_fail_whenWeightIsNegative() {
        // given
        GrowthRecordCreateRequest request = new GrowthRecordCreateRequest(
                LocalDate.of(2026, 11, 9),
                new BigDecimal("85.50"),
                new BigDecimal("-12.30"),
                "몸무게 음수 테스트"
        );

        // when
        Set<ConstraintViolation<GrowthRecordCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("weightKg");
    }

    @Test
    void createGrowth_fail_whenMeasuredDateIsFuture() {
        // given
        GrowthRecordCreateRequest request = new GrowthRecordCreateRequest(
                LocalDate.now().plusDays(1),
                new BigDecimal("72.50"),
                new BigDecimal("9.25"),
                "미래 날짜 테스트"
        );

        // when
        Set<ConstraintViolation<GrowthRecordCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("measuredDate");
    }
}