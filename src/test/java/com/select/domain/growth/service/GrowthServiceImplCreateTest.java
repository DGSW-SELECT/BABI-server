package com.select.domain.growth.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.entity.GrowthRecord;
import com.select.domain.growth.repository.GrowthRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrowthServiceImplCreateTest {
    @Mock
    private GrowthRepository growthRepository;

    @Mock
    private BabyMemberRepository babyMemberRepository;

    @Mock
    private BabyRepository babyRepository;

    @InjectMocks
    private GrowthServiceImpl growthService;

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
    void createGrowth_success() {
        Long userId = 1L;
        Long babyId = 10L;

        // given
        LocalDate measuredDate = LocalDate.of(2026, 11, 9);

        GrowthRecordCreateRequest request =
                new GrowthRecordCreateRequest(
                        measuredDate,
                        new BigDecimal("72.50"),
                        new BigDecimal("9.25"),
                        "정기 성장 측정"
                );

        BabyMember member = mock(BabyMember.class);
        Baby baby = mock(Baby.class);

        when(babyMemberRepository.findByBabyIdAndUserId(
                babyId, userId
        )).thenReturn(Optional.of(member));

        when(babyRepository.findByIdForUpdate(babyId))
                .thenReturn(Optional.of(baby));

        when(member.getId()).thenReturn(100L);

        GrowthRecord saved = mock(GrowthRecord.class);

        when(growthRepository.save(any(GrowthRecord.class)))
                .thenReturn(saved);

        when(saved.getId()).thenReturn(1L);
        when(saved.getHeight()).thenReturn(new BigDecimal("72.50"));
        when(saved.getWeight()).thenReturn(new BigDecimal("9.25"));
        when(saved.getMeasuredDate()).thenReturn(measuredDate);
        when(saved.getMemo()).thenReturn("정기 성장 측정");
        when(saved.getCreatedAt()).thenReturn(LocalDateTime.now());

        // when
        GrowthRecordCreateResponse response =
                growthService.createGrowth(babyId, userId, request);

        // then
        assertNotNull(response);
        assertEquals(1L, userId);
        assertEquals(babyId, response.babyId());
        assertEquals(new BigDecimal("72.50"), response.height());
        assertEquals(new BigDecimal("9.25"), response.weight());
        assertEquals(measuredDate, response.measuredDate());
        assertEquals("정기 성장 측정", response.memo());

        verify(growthRepository).save(any(GrowthRecord.class));

    }
    @Test
    void createGrowth_fail_whenHeightAndWeightAreNull() {
        // given
        Long babyId = 1L;
        Long userId = 10L;

        GrowthRecordCreateRequest request =
                new GrowthRecordCreateRequest(
                        LocalDate.of(2026, 11, 9),
                        null,
                        null,
                        null
                );

        // when & then
        when(babyMemberRepository.findByBabyIdAndUserId(
                babyId, userId
        )).thenReturn(Optional.of(member));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> growthService.createGrowth(babyId, userId, request)
        );

        assertEquals(
                ErrorCode.GROWTH_VALUE_REQUIRED,
                exception.getErrorCode()
        );

        verify(growthRepository, never()).save(any());
    }
    @Test
    void createGrowth_fail_whenUserIsNotBabyMember() {
        // given
        when(babyMemberRepository.findByBabyIdAndUserId(1L, 10L))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> growthService.createGrowth(1L, 10L, request)
        );

        assertEquals(
                ErrorCode.BABY_MEMBER_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(growthRepository, never()).save(any());
    }
    @Test
    void createGrowth_fail_whenBabyNotFound() {
        // given
        when(babyMemberRepository.findByBabyIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(member));

        when(babyRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                BusinessException.class,
                () -> growthService.createGrowth(1L, 10L, request)
        );

        verify(growthRepository, never()).save(any());
    }
    @Test
    void createGrowth_fail_whenDuplicateDate() {
        // given
        when(babyMemberRepository.findByBabyIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(member));

        when(growthRepository.existsByBaby_IdAndMeasuredDate(
                1L, request.measuredDate()
        )).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> growthService.createGrowth(1L, 10L, request)
        );

        assertEquals(
                ErrorCode.GROWTH_DUPLICATE_DATE,
                exception.getErrorCode()
        );

        verify(growthRepository, never()).save(any());
    }
    @Test
    void createGrowth_success_whenHeightOnly() {
        // given
        when(babyMemberRepository.findByBabyIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(member));

        when(babyRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(baby));

        GrowthRecordCreateRequest request = new GrowthRecordCreateRequest(
                LocalDate.of(2026, 11, 9),
                new BigDecimal("85.50"),
                null,
                "키만 측정"
        );

        GrowthRecord saved = new GrowthRecord(
                baby,
                member.getId(),
                new BigDecimal("85.50"),
                null,
                request.measuredDate(),
                request.memo()
        );

        when(growthRepository.save(any(GrowthRecord.class)))
                .thenReturn(saved);

        // when
        GrowthRecordCreateResponse response =
                growthService.createGrowth(1L, 10L, request);

        // then
        assertThat(response.height()).isEqualByComparingTo("85.50");
        assertThat(response.weight()).isNull();

        verify(growthRepository).save(any(GrowthRecord.class));
    }

    @Test
    void createGrowth_success_whenWeightOnly() {
        // given
        when(babyMemberRepository.findByBabyIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(member));

        when(babyRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(baby));

        GrowthRecordCreateRequest request = new GrowthRecordCreateRequest(
                LocalDate.of(2026, 11, 9),
                null,
                new BigDecimal("12.30"),
                "몸무게만 측정"
        );

        GrowthRecord saved = new GrowthRecord(
                baby,
                member.getId(),
                null,
                new BigDecimal("12.30"),
                request.measuredDate(),
                request.memo()
        );

        when(growthRepository.save(any(GrowthRecord.class)))
                .thenReturn(saved);

        // when
        GrowthRecordCreateResponse response =
                growthService.createGrowth(1L, 10L, request);

        // then
        assertThat(response.height()).isNull();
        assertThat(response.weight()).isEqualByComparingTo("12.30");

        verify(growthRepository).save(any(GrowthRecord.class));
    }
    @Test
    void createGrowth_fail_whenMemberRemoved() {
        // given
        when(babyMemberRepository.findByBabyIdAndUserId(1L, 10L))
                .thenReturn(Optional.empty());

        GrowthRecordCreateRequest request = new GrowthRecordCreateRequest(
                LocalDate.of(2026, 11, 9),
                new BigDecimal("85.50"),
                new BigDecimal("12.30"),
                "탈퇴한 멤버 테스트"
        );

        // when & then
        assertThatThrownBy(() ->
                growthService.createGrowth(1L, 10L, request)
        ).isInstanceOf(BusinessException.class);

        verify(growthRepository, never()).save(any(GrowthRecord.class));
    }

}