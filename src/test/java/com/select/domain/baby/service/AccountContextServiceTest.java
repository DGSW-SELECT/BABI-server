package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.exception.BabySelectionRequiredException;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountContextServiceTest {

    private static final Long USER_ID = 10L;

    @Mock
    private BabyAccessRepository babyAccessRepository;

    @Mock
    private BabyRepository babyRepository;

    @InjectMocks
    private AccountContextService accountContextService;

    @Test
    void resolvesSingleAccessibleBabyWithoutBabyId() {
        when(babyAccessRepository.findAccessibleMembers(USER_ID, AccountContextService.CARE_WRITE, true))
                .thenReturn(List.of(new BabyAccessRepository.BabyMemberAccess(1L, 11L)));

        AccountContextService.AccountContext result = accountContextService.resolveForWrite(USER_ID, null);

        assertThat(result).isEqualTo(new AccountContextService.AccountContext(1L, 11L));
        verify(babyAccessRepository).findAccessibleMembers(USER_ID, AccountContextService.CARE_WRITE, true);
    }

    @Test
    void requiresBabySelectionWhenMultipleBabiesAreAccessible() {
        when(babyAccessRepository.findAccessibleMembers(USER_ID, AccountContextService.CARE_READ, false))
                .thenReturn(List.of(
                        new BabyAccessRepository.BabyMemberAccess(1L, 11L),
                        new BabyAccessRepository.BabyMemberAccess(2L, 22L)
                ));

        assertThatThrownBy(() -> accountContextService.resolveForRead(USER_ID, null))
                .isInstanceOf(BabySelectionRequiredException.class);
    }

    @Test
    void deniesWhenUserHasNoAccessibleBabyForRequestedPermission() {
        when(babyAccessRepository.findAccessibleMembers(USER_ID, AccountContextService.CARE_WRITE, true))
                .thenReturn(List.of());

        assertThatThrownBy(() -> accountContextService.resolveForWrite(USER_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validatesRequestedBabyAndPermission() {
        when(babyRepository.findById(1L)).thenReturn(Optional.of(Baby.builder()
                .name("아기")
                .birthDate(java.time.LocalDate.of(2025, 1, 1))
                .createdByUserId(USER_ID)
                .build()));
        when(babyAccessRepository.findMemberId(USER_ID, 1L, AccountContextService.CARE_WRITE, true))
                .thenReturn(Optional.of(11L));

        AccountContextService.AccountContext result = accountContextService.resolveForWrite(USER_ID, 1L);

        assertThat(result).isEqualTo(new AccountContextService.AccountContext(1L, 11L));
    }
}
