package com.select.domain.growth.service;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.entity.GrowthRecord;
import com.select.domain.growth.repository.GrowthRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrowthServiceListTest {
    @Test void returnsCursorOnlyWhenMoreRowsExist() {
        var repo = mock(GrowthRepository.class);
        var members = mock(BabyMemberRepository.class);
        when(members.findByBabyIdAndUserId(1L, 2L)).thenReturn(Optional.of(mock(BabyMember.class)));
        var a = mock(GrowthRecord.class); var b = mock(GrowthRecord.class); var c = mock(GrowthRecord.class);
        when(b.getId()).thenReturn(20L);
        when(repo.findPage(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(List.of(a,b,c));
        var service = new GrowthServiceImpl(repo, members, mock(BabyRepository.class));
        var page = service.listGrowth(1L, 2L, null, null, null, 2);
        assertEquals(2, page.item().size());
        assertEquals(20L, page.nextCursor());
    }
    @Test void rejectsNonmembersAndInvalidRange() {
        var repo = mock(GrowthRepository.class); var members = mock(BabyMemberRepository.class);
        var service = new GrowthServiceImpl(repo, members, mock(BabyRepository.class));
        assertThrows(BusinessException.class, () -> service.listGrowth(1L, 2L, null, null, null, 20));
        when(members.findByBabyIdAndUserId(1L, 2L)).thenReturn(Optional.of(mock(BabyMember.class)));
        assertThrows(BusinessException.class, () -> service.listGrowth(1L, 2L, LocalDate.of(2026,2,1), LocalDate.of(2026,1,1), null, 20));
        assertThrows(BusinessException.class, () -> service.listGrowth(1L, 2L, null, null, null, 101));
        verifyNoInteractions(repo);
    }
}
