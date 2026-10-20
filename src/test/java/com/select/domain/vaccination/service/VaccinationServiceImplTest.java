package com.select.domain.vaccination.service;

import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationServiceImplTest{
    private BabyVaccinationRepository repository;
    private VaccinationServiceImpl service;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        service = new VaccinationServiceImpl(repository, vaccineRepository, babyRepository);
    }

    @Test
    // 정상적인 요청이면 예방접종 일정 반환
    void GOOD_OFFER_VACCINE_SCHEDULE_CHANGE() {
        Long BabyId = 1L;
        LocalDate from = LocalDate.of(2026,1,1);
        LocalDate to = LocalDate.of(2026,12,31);

        Vaccine vaccine = mock(Vaccine.class);
        BabyVaccination vaccination = mock(BabyVaccination.class);

        when(vaccination.getId()).thenReturn(10L);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccine.getId()).thenReturn(20L);
        when(vaccine.getName()).thenReturn("B형간염");
        when(vaccine.getDoseNumber()).thenReturn(1);
        when(vaccine.getRecommendedStartMonth()).thenReturn(0);
        when(vaccine.getRecommendedEndMonth()).thenReturn(1);
        when(vaccination.getScheduledDate()).thenReturn(LocalDate.of(2026, 2,1));
        when(vaccination.getCompletedDate()).thenReturn(null);
        when(vaccination.getCompleted()).thenReturn(false);

        when(repository
                .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                    BabyId, from, to))
                .thenReturn(List.of(vaccination));
        // when
        VaccinationScheduleResponse response =
                service.getVaccinationSchedules(BabyId, from, to);

        // then
        assertEquals(BabyId, response.babyId());
        assertEquals(1, response.vaccinationSchedules().size());

        // verify
        verify(repository)
                .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                        BabyId, from, to
                );
    }

    @Test
    // 스케줄이 없으면 빈 목록 반환
    void NO_SCHEDULE_RETURN_EMPTY_LIST() {
        Long babyId = 1L;
        LocalDate from = LocalDate.of(2026,1,1);
        LocalDate to = LocalDate.of(2026,12,31);

        when(repository
                .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                        babyId, from, to
                ))
                .thenReturn(List.of());
        // when
        VaccinationScheduleResponse response =
                service.getVaccinationSchedules(babyId, from, to);

        // then
        assertEquals(babyId, response.babyId());
        assertNotNull(response.vaccinationSchedules());
        assertTrue(response.vaccinationSchedules().isEmpty());
    }

    @Test
    // 잘못된 아기 아이디를 받으면 예외 발생
    void WRONG_BABY_ID_THROW_EXCEPTION() {
        assertThrows(
                BusinessException.class,
                () -> service.getVaccinationSchedules(
                        0L,
                        LocalDate.of(2026,1,1),
                        LocalDate.of(2026,12,31)
                )
        );
        verifyNoInteractions(repository);
    }

    @Test
    // 조회 기간이 366일이 넘어가면 예외 발생
    void GET_DATE_UPTO_366_THROW_EXCEPTION() {
        assertThrows(
                BusinessException.class,
                () -> service.getVaccinationSchedules(
                        1L,
                        LocalDate.of(2026,1,1),
                        LocalDate.of(2027,1,3)
                )
        );
        verifyNoInteractions(repository);
    }
}