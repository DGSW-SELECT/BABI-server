package com.select.domain.handover.service;

import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.handover.dto.request.HandoverMemoCreateRequest;
import com.select.domain.handover.dto.response.HandoverMemoResponse;
import com.select.domain.handover.entity.HandoverMemo;
import com.select.domain.handover.repository.HandoverMemoRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class HandoverMemoService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final HandoverMemoRepository memos;
    private final BabyAccessRepository access;
    private final BabyRepository babies;
    private final UserRepository users;
    private final HandoverMemoRateLimiter rateLimiter;
    private final TransactionTemplate transactions;

    public HandoverMemoService(HandoverMemoRepository memos, BabyAccessRepository access,
                               BabyRepository babies, UserRepository users, HandoverMemoRateLimiter rateLimiter,
                               PlatformTransactionManager transactionManager) {
        this.memos = memos;
        this.access = access;
        this.babies = babies;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NEVER)
    public HandoverMemoResponse createMemo(Long babyId, Long userId, HandoverMemoCreateRequest request) {
        requireAuthor(userId, babyId);
        rateLimiter.check(userId, babyId);
        return transactions.execute(status -> {
            users.findByIdForUpdate(userId).filter(User::isActive)
                    .orElseThrow(() -> new AccessDeniedException("활성 사용자가 아닙니다."));
            babies.findByIdForUpdate(babyId)
                    .orElseThrow(() -> new AccessDeniedException("메모 작성 권한이 없습니다."));
            Long authorMemberId = requireAuthor(userId, babyId);
            HandoverMemo memo = memos.save(new HandoverMemo(
                    babyId, authorMemberId, request.content(), LocalDateTime.now(SEOUL_ZONE)));
            return new HandoverMemoResponse(memo.getId(), memo.getAuthorMemberId(),
                    memo.getContent(), memo.getCreatedAt());
        });
    }

    private Long requireAuthor(Long userId, Long babyId) {
        return access.findMemberId(userId, babyId, "CARE_WRITE", true)
                .orElseThrow(() -> new AccessDeniedException("메모 작성 권한이 없습니다."));
    }
}
