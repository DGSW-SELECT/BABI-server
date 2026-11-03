package com.select.domain.voice.repository;

import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SttJobRepository extends JpaRepository<SttJob, Long> {

    Optional<SttJob> findByIdAndBabyId(Long id, Long babyId);

    /**
     * 완료·실패 처리를 직렬화해 같은 작업이 중복 저장되는 것을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from SttJob j where j.id = :id")
    Optional<SttJob> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select j.id
            from SttJob j
            where j.status = :status
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
            order by j.id asc
            """)
    List<Long> findReadyIds(
            @Param("status") SttStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 여러 인스턴스가 동시에 같은 작업을 처리하지 않도록 원자적으로 선점한다.
     *
     * @return 선점에 성공하면 1, 다른 인스턴스가 먼저 가져갔으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SttJob j
            set j.status = :processing,
                j.startedAt = :now,
                j.updatedAt = :now,
                j.attemptCount = j.attemptCount + 1
            where j.id = :id
              and j.status = :pending
            """)
    int claim(
            @Param("id") Long id,
            @Param("processing") SttStatus processing,
            @Param("pending") SttStatus pending,
            @Param("now") LocalDateTime now
    );

    List<SttJob> findAllByStatusAndStartedAtBefore(SttStatus status, LocalDateTime threshold);
}
