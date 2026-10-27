package com.select.domain.vaccination.repository;

import com.select.domain.vaccination.entity.BabyVaccination;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BabyVaccinationRepository extends JpaRepository<BabyVaccination, Long> {
    @EntityGraph(attributePaths = "vaccine")
    List<BabyVaccination> findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
            Long babyId,
            LocalDate from,
            LocalDate to
    );

    boolean existsByBabyIdAndVaccine_Id(
            Long babyId,
            Long vaccineId
    );

    List<BabyVaccination> findAllByBabyIdAndCompletedTrue(Long babyId);
}
