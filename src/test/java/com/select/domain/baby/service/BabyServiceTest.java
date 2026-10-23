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
import com.select.domain.baby.response.BabyMemberResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BabyServiceTest {

    private static final Long BABY_ID = 101L;
    private static final Long OWNER_USER_ID = 7L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 15);

    private BabyRepository babyRepository;
    private BabyMemberRepository babyMemberRepository;
    private UserRepository userRepository;
    private BabyService babyService;

    @BeforeEach
    void setUp() {
        babyRepository = mock(BabyRepository.class);
        babyMemberRepository = mock(BabyMemberRepository.class);
        userRepository = mock(UserRepository.class);
        babyService = new BabyService(babyRepository, babyMemberRepository, userRepository);
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

    @Test
    void getBabiesWithoutMembershipsReturnsEmptyList() {
        when(babyMemberRepository.findByUserId(OWNER_USER_ID)).thenReturn(List.of());
        when(babyRepository.findAllById(List.of())).thenReturn(List.of());

        List<BabyReadResponse> responses = babyService.getBabies(OWNER_USER_ID);

        assertEquals(List.of(), responses);
        verify(babyMemberRepository).findByUserId(OWNER_USER_ID);
        verify(babyRepository).findAllById(List.of());
        verifyNoMoreInteractions(babyMemberRepository, babyRepository);
    }

    @ParameterizedTest
    @EnumSource(BabyRole.class)
    void getBabiesWithOneBabyIncludesEitherGuardianRole(BabyRole role) {
        BabyMember member = BabyMember.builder()
                .babyId(BABY_ID).userId(OWNER_USER_ID).role(role).build();
        when(babyMemberRepository.findByUserId(OWNER_USER_ID)).thenReturn(List.of(member));
        when(babyRepository.findAllById(List.of(BABY_ID))).thenReturn(List.of(existingBaby()));

        List<BabyReadResponse> responses = babyService.getBabies(OWNER_USER_ID);

        assertEquals(List.of(new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE)), responses);
        verify(babyMemberRepository).findByUserId(OWNER_USER_ID);
        verify(babyRepository).findAllById(List.of(BABY_ID));
        verifyNoMoreInteractions(babyMemberRepository, babyRepository);
    }

    @Test
    void getBabiesReturnsAllBabiesAcrossOwnerAndMemberRelationships() {
        Long secondBabyId = 202L;
        LocalDate secondBirthDate = BIRTH_DATE.plusDays(10);
        BabyMember owner = BabyMember.builder()
                .babyId(BABY_ID).userId(OWNER_USER_ID).role(BabyRole.OWNER).build();
        BabyMember member = BabyMember.builder()
                .babyId(secondBabyId).userId(OWNER_USER_ID).role(BabyRole.MEMBER).build();
        when(babyMemberRepository.findByUserId(OWNER_USER_ID)).thenReturn(List.of(owner, member));
        when(babyRepository.findAllById(List.of(BABY_ID, secondBabyId))).thenReturn(List.of(
                babyWithId(secondBabyId, "둘째", secondBirthDate, 99L), existingBaby()
        ));

        List<BabyReadResponse> responses = babyService.getBabies(OWNER_USER_ID);

        assertEquals(2, responses.size());
        assertEquals(Set.of(
                new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE),
                new BabyReadResponse(secondBabyId, "둘째", secondBirthDate)
        ), Set.copyOf(responses));
        verify(babyMemberRepository).findByUserId(OWNER_USER_ID);
        verify(babyRepository).findAllById(List.of(BABY_ID, secondBabyId));
        verifyNoMoreInteractions(babyMemberRepository, babyRepository);
    }

    @Test
    void getBabiesKeepsDifferentUsersBabyListsSeparate() {
        Long otherUserId = 99L;
        Long otherBabyId = 303L;
        BabyMember ownMembership = BabyMember.builder()
                .babyId(BABY_ID).userId(OWNER_USER_ID).role(BabyRole.OWNER).build();
        BabyMember otherMembership = BabyMember.builder()
                .babyId(otherBabyId).userId(otherUserId).role(BabyRole.OWNER).build();
        when(babyMemberRepository.findByUserId(OWNER_USER_ID)).thenReturn(List.of(ownMembership));
        when(babyMemberRepository.findByUserId(otherUserId)).thenReturn(List.of(otherMembership));
        when(babyRepository.findAllById(List.of(BABY_ID))).thenReturn(List.of(existingBaby()));
        when(babyRepository.findAllById(List.of(otherBabyId))).thenReturn(List.of(
                babyWithId(otherBabyId, "다른 아기", BIRTH_DATE, otherUserId)
        ));

        List<BabyReadResponse> ownBabies = babyService.getBabies(OWNER_USER_ID);
        List<BabyReadResponse> otherBabies = babyService.getBabies(otherUserId);

        assertEquals(List.of(new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE)), ownBabies);
        assertEquals(List.of(new BabyReadResponse(otherBabyId, "다른 아기", BIRTH_DATE)), otherBabies);
        verify(babyMemberRepository).findByUserId(OWNER_USER_ID);
        verify(babyMemberRepository).findByUserId(otherUserId);
        verify(babyRepository).findAllById(List.of(BABY_ID));
        verify(babyRepository).findAllById(List.of(otherBabyId));
        verifyNoMoreInteractions(babyMemberRepository, babyRepository);
    }

    @ParameterizedTest
    @EnumSource(BabyRole.class)
    void getBabyMembersAllowsOwnersAndMembersAndReturnsUserDetails(BabyRole role) {
        Long userId = role == BabyRole.OWNER ? OWNER_USER_ID : 8L;
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, userId)).thenReturn(true);
        when(babyMemberRepository.findByBabyId(BABY_ID)).thenReturn(guardianRelationships());
        when(userRepository.findAllById(List.of(OWNER_USER_ID, 8L)))
                .thenReturn(List.of(guardianUser(OWNER_USER_ID, "보호자1"), guardianUser(8L, "보호자2")));

        List<BabyMemberResponse> responses = babyService.getBabyMembers(BABY_ID, userId);

        assertEquals(2, responses.size());
        assertEquals(expectedGuardians(), Set.copyOf(responses));
        verify(babyRepository).findById(BABY_ID);
        verify(babyMemberRepository).existsByBabyIdAndUserId(BABY_ID, userId);
        verify(babyMemberRepository).findByBabyId(BABY_ID);
        verify(userRepository).findAllById(List.of(OWNER_USER_ID, 8L));
    }

    @Test
    void getBabyMembersWithoutMembershipRejectsBeforeLoadingGuardianDetails() {
        Long otherUserId = 99L;
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, otherUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> babyService.getBabyMembers(BABY_ID, otherUserId));

        verify(babyRepository).findById(BABY_ID);
        verify(babyMemberRepository).existsByBabyIdAndUserId(BABY_ID, otherUserId);
        verifyNoMoreInteractions(babyMemberRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getBabyMembersWithMissingBabyThrowsBeforeCheckingMembership() {
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.empty());

        assertThrows(BabyNotFoundException.class, () -> babyService.getBabyMembers(BABY_ID, OWNER_USER_ID));

        verify(babyRepository).findById(BABY_ID);
        verifyNoInteractions(babyMemberRepository, userRepository);
    }

    @Test
    void getBabyMembersMatchesNicknamesByUserIdWhenUserQueryOrderIsReversed() {
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, OWNER_USER_ID)).thenReturn(true);
        when(babyMemberRepository.findByBabyId(BABY_ID)).thenReturn(guardianRelationships());
        when(userRepository.findAllById(List.of(OWNER_USER_ID, 8L)))
                .thenReturn(List.of(guardianUser(8L, "보호자2"), guardianUser(OWNER_USER_ID, "보호자1")));

        List<BabyMemberResponse> responses = babyService.getBabyMembers(BABY_ID, OWNER_USER_ID);

        assertEquals(2, responses.size());
        assertEquals(expectedGuardians(), Set.copyOf(responses));
        verify(userRepository).findAllById(List.of(OWNER_USER_ID, 8L));
    }

    @ParameterizedTest
    @ValueSource(longs = {7, 8})
    void getBabyMembersWithPartiallyMissingUsersThrowsIllegalStateException(Long existingUserId) {
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, OWNER_USER_ID)).thenReturn(true);
        when(babyMemberRepository.findByBabyId(BABY_ID)).thenReturn(guardianRelationships());
        when(userRepository.findAllById(List.of(OWNER_USER_ID, 8L)))
                .thenReturn(List.of(guardianUser(existingUserId, "남아 있는 보호자")));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> babyService.getBabyMembers(BABY_ID, OWNER_USER_ID)
        );
        assertEquals("보호자 사용자 정보를 찾을 수 없습니다.", exception.getMessage());

        verify(userRepository).findAllById(List.of(OWNER_USER_ID, 8L));
    }

    @Test
    void getBabyMembersWithAllUsersMissingThrowsIllegalStateException() {
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(existingBaby()));
        when(babyMemberRepository.existsByBabyIdAndUserId(BABY_ID, OWNER_USER_ID)).thenReturn(true);
        when(babyMemberRepository.findByBabyId(BABY_ID)).thenReturn(guardianRelationships());
        when(userRepository.findAllById(List.of(OWNER_USER_ID, 8L))).thenReturn(List.of());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> babyService.getBabyMembers(BABY_ID, OWNER_USER_ID)
        );
        assertEquals("보호자 사용자 정보를 찾을 수 없습니다.", exception.getMessage());

        verify(userRepository).findAllById(List.of(OWNER_USER_ID, 8L));
    }

    private List<BabyMember> guardianRelationships() {
        return List.of(
                BabyMember.builder().babyId(BABY_ID).userId(OWNER_USER_ID).role(BabyRole.OWNER).build(),
                BabyMember.builder().babyId(BABY_ID).userId(8L).role(BabyRole.MEMBER).build()
        );
    }

    private Set<BabyMemberResponse> expectedGuardians() {
        return Set.of(
                new BabyMemberResponse(OWNER_USER_ID, "보호자1", BabyRole.OWNER),
                new BabyMemberResponse(8L, "보호자2", BabyRole.MEMBER)
        );
    }

    private User guardianUser(Long userId, String nickname) {
        User user = User.builder().email("user" + userId + "@example.com")
                .passwordHash("test-only-hash").nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Baby existingBaby() {
        return babyWithId(BABY_ID, "아기", BIRTH_DATE, OWNER_USER_ID);
    }

    private Baby babyWithId(Long babyId, String name, LocalDate birthDate, Long userId) {
        Baby baby = Baby.builder()
                .name(name)
                .birthDate(birthDate)
                .createdByUserId(userId)
                .build();
        ReflectionTestUtils.setField(baby, "id", babyId);
        return baby;
    }
}
