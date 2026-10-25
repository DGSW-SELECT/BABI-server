package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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

    List<BabyMember> findByUserId(
            Long userId
    );

    List<BabyMember> findByBabyId(
            Long babyId
    );

    Optional<BabyMember> findByBabyIdAndUserId(
            Long babyId,
            Long userId
    );
}
