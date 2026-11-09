package com.select.domain.baby.service;

import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BabyInvitationServiceTest {

    private BabyMemberRepository babyMemberRepository;
    private BabyInvitationService babyInvitationService;

    @BeforeEach
    void setUp() {
        babyMemberRepository = mock(BabyMemberRepository.class);
        babyInvitationService = new BabyInvitationService(babyMemberRepository);
    }

    @Test
    void validateOwnerWithOwnerPermissionCompletesWithoutException() {
        Long babyId = 10L;
        Long userId = 20L;
        when(babyMemberRepository.existsByBabyIdAndUserIdAndRole(babyId, userId, BabyRole.OWNER))
                .thenReturn(true);

        assertDoesNotThrow(() -> babyInvitationService.validateOwner(babyId, userId));

        verify(babyMemberRepository).existsByBabyIdAndUserIdAndRole(babyId, userId, BabyRole.OWNER);
    }

    @Test
    void validateOwnerWithoutOwnerPermissionThrowsAccessDeniedException() {
        Long babyId = 30L;
        Long userId = 40L;
        when(babyMemberRepository.existsByBabyIdAndUserIdAndRole(babyId, userId, BabyRole.OWNER))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> babyInvitationService.validateOwner(babyId, userId));

        verify(babyMemberRepository).existsByBabyIdAndUserIdAndRole(babyId, userId, BabyRole.OWNER);
    }
}
