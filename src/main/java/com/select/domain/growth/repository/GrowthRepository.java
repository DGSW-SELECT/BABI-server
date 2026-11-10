package com.select.domain.growth.repository;

import com.select.domain.growth.entity.GrowthRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface GrowthRepository extends JpaRepository<GrowthRecord, Long> {

    boolean existsByBaby_IdAndMeasuredDate(
            Long babyId,
            LocalDate MeasuredDate
    );

    Long babyId(Long babyId);
}
