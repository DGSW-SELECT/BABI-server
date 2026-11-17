package com.select.domain.handover.repository;

import com.select.domain.handover.entity.HandoverMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HandoverMemoRepository extends JpaRepository<HandoverMemo, Long> {

    Optional<HandoverMemo> findTopByBabyIdOrderByCreatedAtDescIdDesc(Long babyId);
}
