package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyInvitationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public interface BabyInvitationCodeRepository extends JpaRepository<BabyInvitationCode, Long> {
    boolean existsByCode(String code);
    Optional<BabyInvitationCode> findFirstByBabyIdAndStatus(Long babyId, String status);
    default boolean existsByCode(byte[] code) { return existsByCode(new String(code, StandardCharsets.US_ASCII)); }
    @Query("select c.babyId from BabyInvitationCode c where c.code = :code and c.status = 'ACTIVE'")
    Optional<Long> findBabyIdByString(@Param("code") String code);
    default Optional<Long> findBabyIdByCode(byte[] code) { return findBabyIdByString(new String(code, StandardCharsets.US_ASCII)); }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BabyInvitationCode c where c.babyId = :babyId and c.status = 'ACTIVE'")
    Optional<BabyInvitationCode> findByIdForUpdate(@Param("babyId") Long babyId);
}
