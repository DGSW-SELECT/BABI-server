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
class BabyVaccinationRepositoryTest {

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
                BabyVaccination.create(babyId, vaccine, scheduledDate,"test","test");

        entityManager.persist(vaccination);

        return vaccination;
    }

    @Test
    void GET_BABY_ID_AND_SCHEDULE() {
        // given
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        createVaccination(babyId1,LocalDate.of(2026, 3, 1), "백신 A");
        createVaccination(babyId1, LocalDate.of(2027, 1, 1), "백신 B");
        createVaccination(babyId2, LocalDate.of(2026, 3, 1), "백신 C");

        entityManager.flush();
        entityManager.clear();

        // when
        var result =
                repository
                        .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                                babyId1, from, to);

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
        createVaccination(babyId1, LocalDate.of(2026, 6, 1), "백신 C");
        createVaccination(babyId1, LocalDate.of(2026, 2, 1), "백신 A");
        createVaccination(babyId1, LocalDate.of(2026, 4, 1), "백신 B");

        entityManager.flush();
        entityManager.clear();

        // when
        var result =
                repository
                        .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                                babyId1,
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
                babyId1,
                LocalDate.of(2026, 3, 1),
                "백신 A"
        );

        Long vaccineId = vaccination.getVaccine().getId();

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result =
                repository.existsByBabyIdAndVaccine_Id(babyId1, vaccineId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void NOT_EXISTS_BY_BABY_ID_AND_VACCINE_ID() {
        // given
        BabyVaccination vaccination = createVaccination(
                babyId1,
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
                babyId1,
                vaccine,
                LocalDate.of(2026, 11, 1),
                "병원명",
                "메모씀"
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
        assertThat(found.getBabyId()).isEqualTo(babyId1);
        assertThat(found.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(found.getVaccine().getName()).isEqualTo("백신 A");
        assertThat(found.getCompleted()).isFalse();
    }
    @Test
    void DELETE_VACCINATION_SUCCESS() {
        // given
        BabyVaccination vaccination = createVaccination(babyId1,LocalDate.of(2026,10,20),"백신 짱");
        BabyVaccination saved = repository.save(vaccination);

        Long id = saved.getId();

        // when
        repository.delete(saved);
        repository.flush();

        // then
        assertThat(repository.findById(id)).isEmpty();
    }
}
