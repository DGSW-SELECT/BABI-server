package com.select.domain.vaccination.service;

import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
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
    @Test
// 정상 등록
    void CREATE_VACCINATION_SCHEDULE_SUCCESS() {
        Long babyId = 1L;
        Long vaccineId = 3L;

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        vaccineId,
                        LocalDate.now().plusDays(1)
                );

        Vaccine vaccine = mock(Vaccine.class);

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));

        when(vaccine.getId()).thenReturn(vaccineId);
        when(vaccine.getName()).thenReturn("B형간염");
        when(vaccine.getDoseNumber()).thenReturn(1);

        when(repository.existsByBabyIdAndVaccine_Id(babyId, vaccineId))
                .thenReturn(false);

        when(repository.save(any(BabyVaccination.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var response = service.createVaccinationSchedule(babyId, request);

        // then
        assertNotNull(response);
        verify(repository).save(any(BabyVaccination.class));
    }

    @Test
// 존재하지 않는 아기
    void CREATE_VACCINATION_SCHEDULE_BABY_NOT_FOUND() {
        Long babyId = 999L;

        var request = new VaccinationScheduleCreateRequest(
                3L, LocalDate.now().plusDays(1)
        );

        when(babyRepository.existsById(babyId)).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
// 존재하지 않는 백신
    void CREATE_VACCINATION_SCHEDULE_VACCINE_NOT_FOUND() {
        Long babyId = 1L;
        Long vaccineId = 999L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId, LocalDate.now().plusDays(1)
        );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
    // 오늘 날짜는 허용하지 않음
    void CREATE_VACCINATION_SCHEDULE_INVALID_DATE() {
        Long babyId = 1L;
        Long vaccineId = 3L;

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        vaccineId,
                        LocalDate.now()
                );

        when(babyRepository.existsById(babyId)).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, request)
        );

        verify(vaccineRepository, never()).findById(any());
        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
// 중복 등록
    void CREATE_VACCINATION_SCHEDULE_DUPLICATE() {
        Long babyId = 1L;
        Long vaccineId = 3L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId, LocalDate.now().plusDays(1)
        );

        Vaccine vaccine = mock(Vaccine.class);

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getId()).thenReturn(vaccineId);

        when(repository.existsByBabyIdAndVaccine_Id(babyId, vaccineId))
                .thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }
    @Test
        // 접종 완료 처리: 완료 여부가 true이고 완료일이 지정됨
    void UPDATE_VACCINATION_SCHEDULE_COMPLETE_SUCCESS() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(3L);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                LocalDate.now().plusDays(7)
        );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleUpdateRequest(true);

        // when
        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                );

        // then
        assertTrue(response.completed());
        assertEquals(LocalDate.now(), response.completedDate());
        assertTrue(vaccination.getCompleted());
    }

    @Test
        // 접종 완료 취소: 완료 여부가 false이고 완료일이 제거됨
    void UPDATE_VACCINATION_SCHEDULE_CANCEL_COMPLETE_SUCCESS() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(3L);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                LocalDate.now().plusDays(7)
        );
        vaccination.complete(LocalDate.now());

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleUpdateRequest(false);

        // when
        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                );

        // then
        assertFalse(response.completed());
        assertNull(response.completedDate());
        assertFalse(vaccination.getCompleted());
    }

    @Test
        // 접종 일정이 존재하지 않으면 예외 발생
    void UPDATE_VACCINATION_SCHEDULE_NOT_FOUND() {
        Long babyId = 1L;
        Long scheduleId = 999L;

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.empty());

        var request = new VaccinationScheduleUpdateRequest(true);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                )
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
        // 다른 아기의 접종 일정은 수정할 수 없음
    void UPDATE_VACCINATION_SCHEDULE_WRONG_BABY() {
        Long requestedBabyId = 1L;
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(3L);

        BabyVaccination vaccination = BabyVaccination.create(
                2L,
                vaccine,
                LocalDate.now().plusDays(7)
        );

        when(babyRepository.existsById(requestedBabyId))
                .thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleUpdateRequest(true);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        requestedBabyId, scheduleId, request
                )
        );

        assertFalse(vaccination.getCompleted());
        assertNull(vaccination.getCompletedDate());
    }
}