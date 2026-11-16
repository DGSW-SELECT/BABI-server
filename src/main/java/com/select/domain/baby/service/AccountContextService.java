package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.exception.BabySelectionRequiredException;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.family.entity.FamilyMember;
import com.select.domain.family.repository.FamilyMemberRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT의 사용자 ID를 음성 기록 저장에 필요한 아기·가족 구성원 ID로 변환한다.
 *
 * <p>요청에 {@code babyId}가 있으면 해당 아기의 구성원인지 검증한다.
 * 없으면 사용자가 속한 아기가 하나일 때만 자동으로 선택하고,
 * 여러 아기에 속한 경우 {@link BabySelectionRequiredException}을 던져
 * 클라이언트가 {@code babyId}를 명시하도록 한다.</p>
 */
@Service
public class AccountContextService {

    private final FamilyMemberRepository familyMemberRepository;
    private final BabyRepository babyRepository;

    public AccountContextService(
            FamilyMemberRepository familyMemberRepository,
            BabyRepository babyRepository
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.babyRepository = babyRepository;
    }

    public AccountContext resolve(Long userId, Long babyId) {
        List<FamilyMember> memberships = familyMemberRepository
                .findAllByUserIdAndStatusOrderByIdAsc(userId, FamilyMember.STATUS_ACTIVE);
        if (memberships.isEmpty()) {
            throw new AccessDeniedException("활성 가족 구성원이 아닙니다.");
        }

        if (babyId != null) {
            return resolveRequestedBaby(memberships, babyId);
        }
        return resolveSingleBaby(memberships);
    }

    private AccountContext resolveRequestedBaby(List<FamilyMember> memberships, Long babyId) {
        Baby baby = babyRepository.findById(babyId)
                .orElseThrow(BabyNotFoundException::new);
        FamilyMember member = memberships.stream()
                .filter(candidate -> candidate.getFamilyGroupId().equals(baby.getFamilyGroupId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("해당 아기의 구성원이 아닙니다."));
        return new AccountContext(baby.getId(), member.getId());
    }

    private AccountContext resolveSingleBaby(List<FamilyMember> memberships) {
        List<AccountContext> candidates = new ArrayList<>();
        for (FamilyMember member : memberships) {
            for (Baby baby : babyRepository.findAllByFamilyGroupIdOrderByIdAsc(member.getFamilyGroupId())) {
                candidates.add(new AccountContext(baby.getId(), member.getId()));
            }
        }

        if (candidates.isEmpty()) {
            throw new BabyNotFoundException();
        }
        if (candidates.size() > 1) {
            throw new BabySelectionRequiredException();
        }
        return candidates.getFirst();
    }

    public record AccountContext(Long babyId, Long familyMemberId) {
    }
}
