package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BabyMemberRepository extends JpaRepository<BabyMember, Long> {

    boolean existsByBabyIdAndUserIdAndRole(
            Long babyId,
            Long userId,
            BabyRole role
    );

    boolean existsByBabyIdAndUserId(
            Long babyId,
            Long userId
    );
}
