package com.select.domain.handover.repository;

import com.select.domain.handover.entity.HandoverBriefing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HandoverBriefingRepository extends JpaRepository<HandoverBriefing, Long> {

    Optional<HandoverBriefing> findTopByGroupIdAndBabyIdOrderByCreatedAtDesc(
            Long groupId,
            Long babyId
    );
}
