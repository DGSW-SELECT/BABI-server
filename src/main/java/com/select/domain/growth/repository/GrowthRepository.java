package com.select.domain.growth.repository;

import com.select.domain.growth.entity.GrowthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

import java.time.LocalDate;

public interface GrowthRepository extends JpaRepository<GrowthRecord, Long> {

    @Query("""
            select g from GrowthRecord g where g.baby.id = :babyId
              and (:fromDate is null or g.measuredDate >= :fromDate)
              and (:toDate is null or g.measuredDate <= :toDate)
              and (:cursor is null or g.id < :cursor)
            order by g.id desc
            """)
    List<GrowthRecord> findPage(@Param("babyId") Long babyId, @Param("fromDate") LocalDate from,
            @Param("toDate") LocalDate to, @Param("cursor") Long cursor, Pageable pageable);

    Optional<GrowthRecord> findByIdAndBaby_Id(Long id, Long babyId);
    Optional<GrowthRecord> findFirstByBaby_IdAndMeasuredDateLessThanOrderByMeasuredDateDescIdDesc(Long babyId, LocalDate date);

    boolean existsByBaby_IdAndMeasuredDate(
            Long babyId,
            LocalDate MeasuredDate
    );
}
