package com.select.domain.growth.service;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.entity.GrowthRecord;
import com.select.domain.growth.repository.GrowthRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrowthComparisonTest {
    @Test void comparesPreviousRecordWithoutInventingMissingMeasurements() {
        var repo = mock(GrowthRepository.class); var members = mock(BabyMemberRepository.class);
        when(members.findByBabyIdAndUserId(1L, 2L)).thenReturn(Optional.of(mock(BabyMember.class)));
        var current = mock(GrowthRecord.class); var old = mock(GrowthRecord.class);
        when(current.getMeasuredDate()).thenReturn(LocalDate.of(2026, 9, 24));
        when(current.getHeight()).thenReturn(new BigDecimal("100.2"));
        when(old.getMeasuredDate()).thenReturn(LocalDate.of(2026, 9, 23));
        when(old.getHeight()).thenReturn(new BigDecimal("100.0"));
        when(repo.findByIdAndBaby_Id(5L, 1L)).thenReturn(Optional.of(current));
        when(repo.findFirstByBaby_IdAndMeasuredDateLessThanOrderByMeasuredDateDescIdDesc(1L, LocalDate.of(2026,9,24))).thenReturn(Optional.of(old));
        var result = new GrowthServiceImpl(repo, members, mock(BabyRepository.class)).compareGrowth(1L, 2L, 5L);
        assertEquals(1L, result.change().days());
        assertEquals(new BigDecimal("0.2"), result.change().heightCm());
        assertNull(result.change().weightKg());
    }
    @Test void noPreviousReturnsNullAndDifferentBabyIsRejected() {
        var repo = mock(GrowthRepository.class); var members = mock(BabyMemberRepository.class);
        when(members.findByBabyIdAndUserId(1L, 2L)).thenReturn(Optional.of(mock(BabyMember.class)));
        var current = mock(GrowthRecord.class);
        when(current.getMeasuredDate()).thenReturn(LocalDate.of(2026,9,24));
        when(repo.findByIdAndBaby_Id(5L, 1L)).thenReturn(Optional.of(current));
        var service = new GrowthServiceImpl(repo, members, mock(BabyRepository.class));
        var result = service.compareGrowth(1L, 2L, 5L);
        assertNull(result.previous()); assertNull(result.change());
        assertThrows(BusinessException.class, () -> service.compareGrowth(1L, 2L, 9L));
        assertThrows(BusinessException.class, () -> service.compareGrowth(2L, 2L, 5L));
    }
}
