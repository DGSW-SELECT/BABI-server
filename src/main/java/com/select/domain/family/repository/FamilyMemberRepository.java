package com.select.domain.family.repository;

import com.select.domain.family.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findAllByUserIdAndStatusOrderByIdAsc(Long userId, String status);
}
