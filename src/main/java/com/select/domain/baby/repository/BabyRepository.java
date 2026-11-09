package com.select.domain.baby.repository;

import com.select.domain.baby.entity.Baby;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BabyRepository extends JpaRepository<Baby, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select baby from Baby baby where baby.id = :babyId")
    Optional<Baby> findByIdForUpdate(@Param("babyId") Long babyId);
}
