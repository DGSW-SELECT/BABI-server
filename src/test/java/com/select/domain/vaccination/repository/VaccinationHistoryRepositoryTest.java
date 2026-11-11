package com.select.domain.vaccination.repository;

import com.select.domain.baby.entity.Baby;
import com.select.domain.user.entity.User;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VaccinationHistoryRepositoryTest {

    @Autowired
    private BabyVaccinationRepository repository;

    @Autowired
    private TestEntityManager entityManager;
    private Long babyId1;
    private Long babyId2;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("test-password")
                .nickname("테스트")
                .build();

        entityManager.persist(user);
        entityManager.flush();

        Baby baby1 = Baby.builder()
                .name("아기1")
                .birthDate(LocalDate.of(2025, 1, 1))
                .createdByUserId(user.getId())
                .build();

        Baby baby2 = Baby.builder()
                .name("아기2")
                .birthDate(LocalDate.of(2025, 2, 1))
                .createdByUserId(user.getId())
                .build();

        entityManager.persist(baby1);
        entityManager.persist(baby2);
        entityManager.flush();

        babyId1 = baby1.getId();
        babyId2 = baby2.getId();
    }

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
                1L, completedVaccine, LocalDate.of(2026, 9, 1),"test","test"
        );
        completed.complete(completedDate);
        entityManager.persist(completed);

        Vaccine incompleteVaccine = createVaccine("미완료 백신", 2);
        BabyVaccination incomplete = BabyVaccination.create(
                1L, incompleteVaccine, LocalDate.of(2026, 10, 1),"test","test"
        );
        entityManager.persist(incomplete);

        Vaccine otherBabyVaccine = createVaccine("다른 아이의 백신", 1);
        BabyVaccination otherBaby = BabyVaccination.create(
                2L, otherBabyVaccine, LocalDate.of(2026, 9, 1), "test", "test"
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