package com.select.domain.handover.repository;

import com.select.domain.handover.entity.HandoverMemo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverMemoRepository extends JpaRepository<HandoverMemo, Long> {
}
