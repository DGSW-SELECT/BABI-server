package com.select.domain.vaccination.repository;

import com.select.domain.vaccination.entity.Vaccine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaccineRepository extends JpaRepository<Vaccine, Long> {
}
