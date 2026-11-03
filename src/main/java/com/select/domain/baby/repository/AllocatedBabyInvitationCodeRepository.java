package com.select.domain.baby.repository;

import com.select.domain.baby.entity.AllocatedBabyInvitationCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocatedBabyInvitationCodeRepository extends JpaRepository<AllocatedBabyInvitationCode, Long> {
    boolean existsByCode(byte[] code);
}
