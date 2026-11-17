package com.select.domain.baby.service;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.exception.BabySelectionRequiredException;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT의 사용자 ID를 음성 기록 저장에 필요한 아기·구성원 ID로 변환한다.
 *
 * <p>권한 판정은 {@link BabyAccessRepository}에 위임해 육아 기록 API와 같은 규칙을 쓴다.
 * 요청에 {@code babyId}가 있으면 해당 아기의 구성원인지 검증한다.
 * 없으면 사용자가 접근할 수 있는 아기가 하나일 때만 자동으로 선택하고,
 * 여러 아기에 속한 경우 {@link BabySelectionRequiredException}을 던져
 * 클라이언트가 {@code babyId}를 명시하도록 한다.</p>
 */
@Service
public class AccountContextService {

    static final String CARE_READ = "CARE_READ";
    static final String CARE_WRITE = "CARE_WRITE";

    private final BabyAccessRepository babyAccessRepository;
    private final BabyMemberRepository babyMemberRepository;
    private final BabyRepository babyRepository;

    public AccountContextService(
            BabyAccessRepository babyAccessRepository,
            BabyMemberRepository babyMemberRepository,
            BabyRepository babyRepository
    ) {
        this.babyAccessRepository = babyAccessRepository;
        this.babyMemberRepository = babyMemberRepository;
        this.babyRepository = babyRepository;
    }

    public AccountContext resolveForRead(Long userId, Long babyId) {
        return resolve(userId, babyId, CARE_READ, false);
    }

    public AccountContext resolveForWrite(Long userId, Long babyId) {
        return resolve(userId, babyId, CARE_WRITE, true);
    }

    private AccountContext resolve(Long userId, Long babyId, String permission, boolean writing) {
        if (babyId != null) {
            return resolveRequestedBaby(userId, babyId, permission, writing);
        }
        return resolveSingleBaby(userId, permission, writing);
    }

    private AccountContext resolveRequestedBaby(
            Long userId,
            Long babyId,
            String permission,
            boolean writing
    ) {
        babyRepository.findById(babyId)
                .orElseThrow(BabyNotFoundException::new);
        Long memberId = babyAccessRepository.findMemberId(userId, babyId, permission, writing)
                .orElseThrow(() -> new AccessDeniedException("해당 아기의 구성원이 아닙니다."));
        return new AccountContext(babyId, memberId);
    }

    private AccountContext resolveSingleBaby(Long userId, String permission, boolean writing) {
        List<BabyMember> memberships = babyMemberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            throw new AccessDeniedException("활성 가족 구성원이 아닙니다.");
        }

        List<AccountContext> candidates = new ArrayList<>();
        for (BabyMember membership : memberships) {
            babyAccessRepository
                    .findMemberId(userId, membership.getBabyId(), permission, writing)
                    .ifPresent(memberId -> candidates.add(
                            new AccountContext(membership.getBabyId(), memberId)
                    ));
        }

        if (candidates.isEmpty()) {
            throw new AccessDeniedException("활성 가족 구성원이 아닙니다.");
        }
        if (candidates.size() > 1) {
            throw new BabySelectionRequiredException();
        }
        return candidates.getFirst();
    }

    public record AccountContext(Long babyId, Long memberId) {
    }
}
