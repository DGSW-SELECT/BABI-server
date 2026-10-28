package com.select.domain.handover.service;

import com.select.domain.handover.dto.request.HandoverMemoCreateRequest;
import com.select.domain.handover.dto.response.HandoverMemoResponse;
import com.select.domain.handover.entity.HandoverMemo;
import com.select.domain.handover.repository.HandoverMemoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional
public class HandoverMemoService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final HandoverMemoRepository handoverMemoRepository;

    public HandoverMemoService(HandoverMemoRepository handoverMemoRepository) {
        this.handoverMemoRepository = handoverMemoRepository;
    }

    public HandoverMemoResponse createMemo(
            Long groupId,
            Long babyId,
            Long authorId,
            HandoverMemoCreateRequest request
    ) {
        HandoverMemo memo = handoverMemoRepository.save(
                new HandoverMemo(
                        groupId,
                        babyId,
                        authorId,
                        request.content(),
                        LocalDateTime.now(SEOUL_ZONE)
                )
        );

        return new HandoverMemoResponse(
                memo.getId(),
                memo.getAuthorId(),
                memo.getContent(),
                memo.getCreatedAt()
        );
    }
}
