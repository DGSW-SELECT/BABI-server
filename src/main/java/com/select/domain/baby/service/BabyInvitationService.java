package com.select.domain.baby.service;

import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BabyInvitationService {

    private final BabyMemberRepository babyMemberRepository;

    public void validateOwner(Long babyId, Long userId) {
        boolean isOwner = babyMemberRepository.existsByBabyIdAndUserIdAndRole(
                babyId,
                userId,
                BabyRole.OWNER
        );

        if (!isOwner) {
            throw new AccessDeniedException("아기 관리 권한이 없습니다.");
        }
    }
}
