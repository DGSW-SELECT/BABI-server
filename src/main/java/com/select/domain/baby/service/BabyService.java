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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
public class BabyService {

    private final BabyRepository babyRepository;
    private final BabyMemberRepository babyMemberRepository;

    @Transactional
    public BabyCreateResponse createBaby(BabyCreateRequest request, Long userId) {
        Baby baby = Baby.builder()
                .name(request.name())
                .birthDate(request.birthDate())
                .createdByUserId(userId)
                .build();
        Baby savedBaby = babyRepository.save(baby);

        BabyMember babyMember = BabyMember.builder()
                .babyId(savedBaby.getId())
                .userId(userId)
                .role(BabyRole.OWNER)
                .build();
        babyMemberRepository.save(babyMember);
        return new BabyCreateResponse(
                savedBaby.getId(),
                savedBaby.getName(),
                savedBaby.getBirthDate());
    }

    @Transactional(readOnly = true)
    public BabyReadResponse getBaby(Long babyId, Long userId) {
        Baby baby = babyRepository
                .findById(babyId)
                .orElseThrow(BabyNotFoundException::new);
        boolean isMember = babyMemberRepository.existsByBabyIdAndUserId(babyId,userId);

        if (!isMember) {
            throw new AccessDeniedException("접근 권한이 없습니다.");
        }

        return new BabyReadResponse(baby.getId(),baby.getName(),baby.getBirthDate());
    }
}
