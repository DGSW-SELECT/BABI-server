package com.select.domain.baby.repository;

import com.select.domain.baby.entity.AllocatedBabyInvitationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public interface AllocatedBabyInvitationCodeRepository extends JpaRepository<AllocatedBabyInvitationCode, String> {
    default boolean existsByCode(byte[] code) {
        return existsById(new String(code, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT));
    }
}
