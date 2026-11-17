package com.select.domain.baby.repository;

import com.select.domain.baby.entity.BabyEmailInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BabyEmailInvitationRepository extends JpaRepository<BabyEmailInvitation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BabyEmailInvitation i where i.babyId = :babyId and i.email = :email and i.status = com.select.domain.baby.entity.BabyEmailInvitation.Status.PENDING")
    Optional<BabyEmailInvitation> findForUpdate(@Param("babyId") Long babyId, @Param("email") String email);

    List<BabyEmailInvitation> findByBabyId(Long babyId);
}
