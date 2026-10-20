package com.select.domain.vaccination.repository;

import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BabyVaccinationRepositoryTest {

    @Autowired
    private BabyVaccinationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private BabyVaccination createVaccination(
            Long babyId,
            LocalDate scheduledDate,
            String vaccineName
    ) {
        Vaccine vaccine = new Vaccine();

        ReflectionTestUtils.setField(vaccine, "name", vaccineName);
        ReflectionTestUtils.setField(vaccine, "doseNumber", 1);
        ReflectionTestUtils.setField(vaccine, "recommendedStartMonth", 0);
        ReflectionTestUtils.setField(vaccine, "recommendedEndMonth", 1);

        entityManager.persist(vaccine);

        BabyVaccination vaccination =
                BabyVaccination.create(babyId, vaccine, scheduledDate);

        entityManager.persist(vaccination);

        return vaccination;
    }

    @Test
    void GET_BABY_ID_AND_SCHEDULE() {
        // given
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        createVaccination(1L, LocalDate.of(2026, 3, 1), "백신 A");
        createVaccination(1L, LocalDate.of(2027, 1, 1), "백신 B");
        createVaccination(2L, LocalDate.of(2026, 3, 1), "백신 C");

        entityManager.flush();
        entityManager.clear();

        // when
        var result =
                repository
                        .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                                1L, from, to);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getVaccine().getName())
                .isEqualTo("백신 A");
    }

    @Test
    void NO_GET_RETURN_EMPTY_LIST() {
        // when
        var result =
                repository
                        .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                                999L,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void FROM_DATE_ASC_SORTING() {
        // given
        createVaccination(1L, LocalDate.of(2026, 6, 1), "백신 C");
        createVaccination(1L, LocalDate.of(2026, 2, 1), "백신 A");
        createVaccination(1L, LocalDate.of(2026, 4, 1), "백신 B");

        entityManager.flush();
        entityManager.clear();

        // when
        var result =
                repository
                        .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                                1L,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31));

        // then
        assertThat(result)
                .extracting(BabyVaccination::getScheduledDate)
                .containsExactly(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 6, 1));
    }
    @Test
    void EXISTS_BY_BABY_ID_AND_VACCINE_ID() {
        // given: 아기 1에게 백신 A의 일정이 존재함
        BabyVaccination vaccination = createVaccination(
                1L,
                LocalDate.of(2026, 3, 1),
                "백신 A"
        );

        Long vaccineId = vaccination.getVaccine().getId();

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result =
                repository.existsByBabyIdAndVaccine_Id(1L, vaccineId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void NOT_EXISTS_BY_BABY_ID_AND_VACCINE_ID() {
        // given
        BabyVaccination vaccination = createVaccination(
                1L,
                LocalDate.of(2026, 3, 1),
                "백신 A"
        );

        Long vaccineId = vaccination.getVaccine().getId();

        entityManager.flush();
        entityManager.clear();

        // when: 존재하지 않는 아기와 백신 ID 조합 확인
        boolean result =
                repository.existsByBabyIdAndVaccine_Id(999L, vaccineId);

        // then
        assertThat(result).isFalse();
    }
    @Test
    void SAVE_VACCINATION_SUCCESS() {
        // given: 백신 데이터 준비
        Vaccine vaccine = new Vaccine();

        ReflectionTestUtils.setField(vaccine, "name", "백신 A");
        ReflectionTestUtils.setField(vaccine, "doseNumber", 1);
        ReflectionTestUtils.setField(vaccine, "recommendedStartMonth", 0);
        ReflectionTestUtils.setField(vaccine, "recommendedEndMonth", 1);

        entityManager.persist(vaccine);
        entityManager.flush();

        BabyVaccination vaccination = BabyVaccination.create(
                1L,
                vaccine,
                LocalDate.of(2026, 11, 1)
        );

        // when: Repository를 통해 저장
        BabyVaccination saved = repository.save(vaccination);

        entityManager.flush();
        Long savedId = saved.getId();
        entityManager.clear();

        // then: DB에서 다시 조회해 검증
        BabyVaccination found = repository.findById(savedId)
                .orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getBabyId()).isEqualTo(1L);
        assertThat(found.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(found.getVaccine().getName()).isEqualTo("백신 A");
        assertThat(found.getCompleted()).isFalse();
    }
}