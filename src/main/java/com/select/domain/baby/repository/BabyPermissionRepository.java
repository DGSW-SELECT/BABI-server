package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.entity.BabyPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BabyPermissionRepository extends JpaRepository<BabyPermission, BabyPermissionId> {
    void deleteByBabyIdAndBabyMemberId(Long babyId, Long babyMemberId);
}
