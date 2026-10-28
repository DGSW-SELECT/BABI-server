package com.select.domain.handover.service;

import com.select.domain.handover.dto.request.HandoverMemoCreateRequest;
import com.select.domain.handover.dto.response.HandoverMemoResponse;
import com.select.domain.handover.entity.HandoverMemo;
import com.select.domain.handover.repository.HandoverMemoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandoverMemoServiceTest {

    private HandoverMemoRepository handoverMemoRepository;
    private HandoverMemoService handoverMemoService;

    @BeforeEach
    void setUp() {
        handoverMemoRepository = mock(HandoverMemoRepository.class);
        handoverMemoService = new HandoverMemoService(handoverMemoRepository);
    }

    @Test
    void savesMemoWithAuthenticatedAuthor() {
        HandoverMemo savedMemo = new HandoverMemo(
                1L,
                2L,
                3L,
                "분유 120ml 먹였습니다.",
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );
        when(handoverMemoRepository.save(any(HandoverMemo.class))).thenReturn(savedMemo);

        HandoverMemoResponse response = handoverMemoService.createMemo(
                1L,
                2L,
                3L,
                new HandoverMemoCreateRequest("분유 120ml 먹였습니다.")
        );

        assertEquals(3L, response.authorId());
        assertEquals("분유 120ml 먹였습니다.", response.content());
    }
}
