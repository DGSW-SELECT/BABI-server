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
class VaccinationHistoryRepositoryTest {

    @Autowired
    private BabyVaccinationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Vaccine createVaccine(String name, int doseNumber) {
        Vaccine vaccine = new Vaccine();
        ReflectionTestUtils.setField(vaccine, "name", name);
        ReflectionTestUtils.setField(vaccine, "doseNumber", doseNumber);
        ReflectionTestUtils.setField(vaccine, "recommendedStartMonth", 0);
        ReflectionTestUtils.setField(vaccine, "recommendedEndMonth", 1);
        entityManager.persist(vaccine);
        return vaccine;
    }

    @Test
    void FIND_ONLY_COMPLETED_VACCINATIONS_FOR_BABY() {
        // given
        LocalDate completedDate = LocalDate.of(2026, 9, 20);

        Vaccine completedVaccine = createVaccine("완료된 백신", 1);
        BabyVaccination completed = BabyVaccination.create(
                1L, completedVaccine, LocalDate.of(2026, 9, 1)
        );
        completed.complete(completedDate);
        entityManager.persist(completed);

        Vaccine incompleteVaccine = createVaccine("미완료 백신", 2);
        BabyVaccination incomplete = BabyVaccination.create(
                1L, incompleteVaccine, LocalDate.of(2026, 10, 1)
        );
        entityManager.persist(incomplete);

        Vaccine otherBabyVaccine = createVaccine("다른 아이의 백신", 1);
        BabyVaccination otherBaby = BabyVaccination.create(
                2L, otherBabyVaccine, LocalDate.of(2026, 9, 1)
        );
        otherBaby.complete(LocalDate.of(2026, 9, 2));
        entityManager.persist(otherBaby);

        entityManager.flush();
        entityManager.clear();

        // when
        var result = repository.findAllByBabyIdAndCompletedTrue(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getVaccine().getName()).isEqualTo("완료된 백신");
        assertThat(result.getFirst().getCompleted()).isTrue();
        assertThat(result.getFirst().getCompletedDate()).isEqualTo(completedDate);
    }
}