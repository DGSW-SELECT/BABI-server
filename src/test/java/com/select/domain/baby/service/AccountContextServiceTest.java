package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.exception.BabySelectionRequiredException;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.family.entity.FamilyMember;
import com.select.domain.family.repository.FamilyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountContextServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long FAMILY_GROUP_ID = 100L;
    private static final Long BABY_ID = 1L;
    private static final Long FAMILY_MEMBER_ID = 7L;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private BabyRepository babyRepository;

    @InjectMocks
    private AccountContextService accountContextService;

    @Test
    void resolveWithBabyIdReturnsMembership() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of(member(FAMILY_GROUP_ID)));
        when(babyRepository.findById(BABY_ID))
                .thenReturn(Optional.of(baby(BABY_ID, FAMILY_GROUP_ID)));

        AccountContextService.AccountContext context =
                accountContextService.resolve(USER_ID, BABY_ID);

        assertThat(context.babyId()).isEqualTo(BABY_ID);
        assertThat(context.familyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
    }

    @Test
    void resolveWithUnknownBabyThrowsNotFound() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of(member(FAMILY_GROUP_ID)));
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountContextService.resolve(USER_ID, BABY_ID))
                .isInstanceOf(BabyNotFoundException.class);
    }

    @Test
    void resolveWithBabyFromAnotherFamilyThrowsAccessDenied() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of(member(FAMILY_GROUP_ID)));
        when(babyRepository.findById(BABY_ID))
                .thenReturn(Optional.of(baby(BABY_ID, 999L)));

        assertThatThrownBy(() -> accountContextService.resolve(USER_ID, BABY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolveWithoutBabyIdSelectsSingleBaby() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of(member(FAMILY_GROUP_ID)));
        when(babyRepository.findAllByFamilyGroupIdOrderByIdAsc(FAMILY_GROUP_ID))
                .thenReturn(List.of(baby(BABY_ID, FAMILY_GROUP_ID)));

        AccountContextService.AccountContext context =
                accountContextService.resolve(USER_ID, null);

        assertThat(context.babyId()).isEqualTo(BABY_ID);
        assertThat(context.familyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
    }

    @Test
    void resolveWithoutMembershipThrowsAccessDenied() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of());

        assertThatThrownBy(() -> accountContextService.resolve(USER_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolveWithoutBabiesThrowsNotFound() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of(member(FAMILY_GROUP_ID)));
        when(babyRepository.findAllByFamilyGroupIdOrderByIdAsc(FAMILY_GROUP_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> accountContextService.resolve(USER_ID, null))
                .isInstanceOf(BabyNotFoundException.class);
    }

    @Test
    void resolveWithMultipleBabiesRequiresSelection() {
        when(familyMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(
                USER_ID, FamilyMember.STATUS_ACTIVE
        )).thenReturn(List.of(member(FAMILY_GROUP_ID)));
        when(babyRepository.findAllByFamilyGroupIdOrderByIdAsc(FAMILY_GROUP_ID))
                .thenReturn(List.of(
                        baby(BABY_ID, FAMILY_GROUP_ID),
                        baby(2L, FAMILY_GROUP_ID)
                ));

        assertThatThrownBy(() -> accountContextService.resolve(USER_ID, null))
                .isInstanceOf(BabySelectionRequiredException.class);
    }

    private static FamilyMember member(Long familyGroupId) {
        FamilyMember member = FamilyMember.builder()
                .familyGroupId(familyGroupId)
                .userId(USER_ID)
                .role("OWNER")
                .status(FamilyMember.STATUS_ACTIVE)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(
                member, "id", FAMILY_MEMBER_ID
        );
        return member;
    }

    private static Baby baby(Long id, Long familyGroupId) {
        Baby baby = Baby.builder()
                .familyGroupId(familyGroupId)
                .name("테스트 아기")
                .birthDate(LocalDate.of(2026, 1, 1))
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(baby, "id", id);
        return baby;
    }
}
