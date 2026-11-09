package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BabyMemberRepository extends JpaRepository<BabyMember, Long> {

    @Query("select (count(m) > 0) from BabyMember m where m.babyId = :babyId and m.userId = :userId and m.role = :role and m.removedAt is null")
    boolean existsByBabyIdAndUserIdAndRole(
            @Param("babyId") Long babyId,
            @Param("userId") Long userId,
            @Param("role") BabyRole role
    );

    @Query("select (count(m) > 0) from BabyMember m where m.babyId = :babyId and m.userId = :userId and m.removedAt is null")
    boolean existsByBabyIdAndUserId(
            @Param("babyId") Long babyId,
            @Param("userId") Long userId
    );

    @Query("""
            select (count(m) > 0)
            from BabyMember m
            where m.babyId = :babyId
              and m.userId = :userId
              and m.removedAt is null
              and (
                    m.role = com.select.domain.baby.entity.BabyRole.OWNER
                    or exists (
                        select p
                        from BabyPermission p
                        where p.babyId = m.babyId
                          and p.babyMemberId = m.id
                          and p.permission = com.select.domain.baby.entity.BabyPermission.CARE_ALL
                    )
              )
            """)
    boolean hasCareAccess(
            @Param("babyId") Long babyId,
            @Param("userId") Long userId
    );

    @Query("select m from BabyMember m where m.userId = :userId and m.removedAt is null")
    List<BabyMember> findByUserId(
            @Param("userId") Long userId
    );

    @Query("select m from BabyMember m where m.babyId = :babyId and m.removedAt is null")
    List<BabyMember> findByBabyId(
            @Param("babyId") Long babyId
    );

    @Query("select m from BabyMember m where m.babyId = :babyId and m.userId = :userId and m.removedAt is null")
    Optional<BabyMember> findByBabyIdAndUserId(
            @Param("babyId") Long babyId,
            @Param("userId") Long userId
    );

    @Query("select m from BabyMember m where m.babyId = :babyId and m.userId = :userId")
    Optional<BabyMember> findIncludingRemoved(@Param("babyId") Long babyId, @Param("userId") Long userId);
}
