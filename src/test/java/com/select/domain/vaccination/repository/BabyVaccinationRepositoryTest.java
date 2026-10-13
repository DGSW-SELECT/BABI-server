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

    private void createVaccination(
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

        BabyVaccination vaccination = BabyVaccination.create(babyId, vaccine, scheduledDate);

        entityManager.persist(vaccination);
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
}