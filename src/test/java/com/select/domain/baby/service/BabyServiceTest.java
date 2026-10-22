package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyReadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BabyServiceTest {

    private static final Long BABY_ID = 101L;
    private static final Long OWNER_USER_ID = 7L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 15);

    private BabyRepository babyRepository;
    private BabyMemberRepository babyMemberRepository;
    private BabyService babyService;

    @BeforeEach
    void setUp() {
        babyRepository = mock(BabyRepository.class);
        babyMemberRepository = mock(BabyMemberRepository.class);
        babyService = new BabyService(babyRepository, babyMemberRepository);
    }

    @Test
    void createBabySavesRegisteringUserAsOwnerAndReturnsBabyId() {
        BabyCreateRequest request = new BabyCreateRequest("아기", BIRTH_DATE);
        when(babyRepository.save(any(Baby.class))).thenAnswer(invocation -> {
            Baby baby = invocation.getArgument(0);
            ReflectionTestUtils.setField(baby, "id", BABY_ID);
            return baby;
        });

        BabyCreateResponse response = babyService.createBaby(request, OWNER_USER_ID);

        ArgumentCaptor<Baby> babyCaptor = ArgumentCaptor.forClass(Baby.class);
        ArgumentCaptor<BabyMember> memberCaptor = ArgumentCaptor.forClass(BabyMember.class);
        var order = inOrder(babyRepository, babyMemberRepository);
        order.verify(babyRepository).save(babyCaptor.capture());
        order.verify(babyMemberRepository).save(memberCaptor.capture());
        assertEquals(request.name(), babyCaptor.getValue().getName());
        assertEquals(request.birthDate(), babyCaptor.getValue().getBirthDate());
        assertEquals(OWNER_USER_ID, babyCaptor.getValue().getCreatedByUserId());
        assertEquals(BABY_ID, memberCaptor.getValue().getBabyId());
        assertEquals(OWNER_USER_ID, memberCaptor.getValue().getUserId());
        assertEquals(BabyRole.OWNER, memberCaptor.getValue().getRole());
        assertEquals(new BabyCreateResponse(BABY_ID, request.name(), BIRTH_DATE), response);
        assertEquals(BABY_ID, response.babyId());
    }

    @ParameterizedTest
    @EnumSource(BabyRole.class)
    void getBabyAllowsOwnersAndMembers(BabyRole role) {
        Long userId = role == BabyRole.OWNER ? OWNER_USER_ID : 8L;
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, userId)).thenReturn(true);

        BabyReadResponse response = babyService.getBaby(BABY_ID, userId);

        assertEquals(new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE), response);
        verify(babyRepository).findById(BABY_ID);
        verify(babyMemberRepository).existsByBabyIdAndUserId(BABY_ID, userId);
    }

    @Test
    void getBabyWithMissingBabyThrowsBabyNotFoundException() {
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.empty());

        assertThrows(BabyNotFoundException.class, () -> babyService.getBaby(BABY_ID, OWNER_USER_ID));

        verify(babyRepository).findById(BABY_ID);
        verifyNoInteractions(babyMemberRepository);
    }

    @Test
    void getBabyWithoutMembershipThrowsAccessDeniedException() {
        Long otherUserId = 99L;
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, otherUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> babyService.getBaby(BABY_ID, otherUserId));

        verify(babyRepository).findById(BABY_ID);
        verify(babyMemberRepository).existsByBabyIdAndUserId(BABY_ID, otherUserId);
    }

    private Baby existingBaby() {
        Baby baby = Baby.builder()
                .name("아기")
                .birthDate(BIRTH_DATE)
                .createdByUserId(OWNER_USER_ID)
                .build();
        ReflectionTestUtils.setField(baby, "id", BABY_ID);
        return baby;
    }
}
