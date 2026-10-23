package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyMemberResponse;
import com.select.domain.baby.response.BabyReadResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static aQute.bnd.annotation.headers.Category.users;


@Service
@RequiredArgsConstructor
public class BabyService {

    private final BabyRepository babyRepository;
    private final BabyMemberRepository babyMemberRepository;
    private final UserRepository userRepository;

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

    @Transactional(readOnly = true)
    public List<BabyReadResponse> getBabies(Long userId) {
        List<BabyMember> members = babyMemberRepository.findByUserId(userId);
        List<Long> babyIds = members.stream()
                    .map(BabyMember::getBabyId)
                    .toList();
        List<Baby> babies = babyRepository.findAllById(babyIds);
        return babies.stream()
                .map(baby -> new BabyReadResponse(
                baby.getId(),
                baby.getName(),
                baby.getBirthDate()
        )).toList();
    }

    @Transactional(readOnly = true)
    public List<BabyMemberResponse> getBabyMembers(Long babyId, Long userId) {
        babyRepository
                .findById(babyId)
                .orElseThrow(BabyNotFoundException::new);
        boolean isMember = babyMemberRepository.existsByBabyIdAndUserId(babyId,userId);
        if (!isMember) {
            throw new AccessDeniedException("접근 권한이 없습니다.");
        }
        List<BabyMember> members = babyMemberRepository.findByBabyId(babyId);
        List<Long> userIds = members.stream()
                .map(BabyMember::getUserId)
                .toList();
        List<User> users = userRepository.findAllById(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors
                        .toMap(User::getId, user -> user));
        List<BabyMemberResponse> responses = members.stream()
                .map(member -> {
                    User user = userMap.get(member.getUserId());
                    if (user == null) {
                        throw new IllegalStateException("보호자 사용자 정보를 찾을 수 없습니다.");
                    }
                    return new BabyMemberResponse(
                            member.getUserId(),
                            user.getNickname(),
                            member.getRole()
                    );
                })
                .toList();
        return responses;
    }
}
