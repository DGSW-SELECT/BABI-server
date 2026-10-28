package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.exception.InvalidOwnershipTransferException;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.request.BabyUpdateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyMemberResponse;
import com.select.domain.baby.response.BabyReadResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Transactional
    public BabyReadResponse updateBaby(Long babyId, Long userId, BabyUpdateRequest request) {
        Baby baby = babyRepository.findByIdForUpdate(babyId)
                .orElseThrow(BabyNotFoundException::new);
        if (!babyMemberRepository.existsByBabyIdAndUserIdAndRole(babyId, userId, BabyRole.OWNER)) {
            throw new AccessDeniedException("아기 관리 권한이 없습니다.");
        }
        baby.updateDetails(request.name(), request.birthDate());
        return new BabyReadResponse(baby.getId(), baby.getName(), baby.getBirthDate());
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

    @Transactional
    public void leaveBaby(Long babyId, Long userId) {
            // Serialize membership changes with ownership transfers before checking roles.
            // Missing memberships retain the existing FORBIDDEN response, including missing babies.
            babyRepository.findByIdForUpdate(babyId);
            BabyMember member = babyMemberRepository.findByBabyIdAndUserId(babyId, userId)
                    .orElseThrow(() -> new AccessDeniedException("해당 아기의 보호자가 아닙니다."));
            if (member.getRole() == BabyRole.OWNER) {
                throw new AccessDeniedException("관리자는 권한을 위임한 후 탈퇴할 수 있습니다.");
            }
            babyMemberRepository.delete(member);
    }

    @Transactional
    public void transferOwnership(Long babyId, Long userId, Long newOwnerUserId) {
        if (userId.equals(newOwnerUserId)) {
            throw new InvalidOwnershipTransferException();
        }
        babyRepository.findByIdForUpdate(babyId);
        BabyMember owner = babyMemberRepository.findByBabyIdAndUserId(babyId,userId)
                .orElseThrow(() -> new AccessDeniedException("해당 아기의 보호자가 아닙니다."));
        if (owner.getRole() != BabyRole.OWNER) {
            throw new AccessDeniedException("관리자가 아닙니다");
        }
        BabyMember newOwner = babyMemberRepository.findByBabyIdAndUserId(babyId,newOwnerUserId)
                .orElseThrow(() -> new AccessDeniedException("해당 아기의 보호자가 아닙니다."));
        if (newOwner.getRole() != BabyRole.MEMBER) {
            throw new AccessDeniedException("관리자에게 권한을 위임할수 없습니다.");
        }
        owner.changeRole(BabyRole.MEMBER);
        newOwner.changeRole(BabyRole.OWNER);
    }
}
