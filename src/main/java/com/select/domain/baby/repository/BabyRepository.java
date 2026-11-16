package com.select.domain.baby.repository;

import com.select.domain.baby.entity.Baby;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BabyRepository extends JpaRepository<Baby, Long> {

    List<Baby> findAllByFamilyGroupIdOrderByIdAsc(Long familyGroupId);
}
