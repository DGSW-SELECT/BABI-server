package com.select.domain.user.repository;

import com.select.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User,Long> {

    boolean existsByLoginId(String loginId);
    
}
