package com.select.domain.handover.repository;

import com.select.domain.handover.entity.HandoverMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface HandoverMemoRepository extends JpaRepository<HandoverMemo, Long> {

    List<HandoverMemo> findByGroupIdAndBabyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long groupId,
            Long babyId,
            LocalDateTime start,
            LocalDateTime end
    );
}
