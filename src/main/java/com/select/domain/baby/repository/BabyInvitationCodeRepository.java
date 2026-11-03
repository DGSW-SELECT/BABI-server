package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyInvitationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BabyInvitationCodeRepository extends JpaRepository<BabyInvitationCode, Long> {
    boolean existsByCode(byte[] code);

    @Query("select c.babyId from BabyInvitationCode c where c.code = :code")
    Optional<Long> findBabyIdByCode(@Param("code") byte[] code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BabyInvitationCode c where c.babyId = :babyId")
    Optional<BabyInvitationCode> findByIdForUpdate(@Param("babyId") Long babyId);
}
