package com.select.domain.vaccination.service;

import com.select.domain.baby.repository.BabyRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationServiceUpdateTest {
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
    void UPDATE_VACCINATION_SCHEDULE_SUCCESS() {
        // given
        Long babyId = 1L;
        Long scheduleId = 10L;
        Long vaccineId = 3L;

        LocalDate scheduledDate = LocalDate.of(2026, 12, 15);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        vaccineId,
                        scheduledDate,
                        "어디든지",
                        "많이 아픔"
                );

        BabyVaccination vaccination = mock(BabyVaccination.class);
        Vaccine vaccine = mock(Vaccine.class);

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccination.getCompleted()).thenReturn(false);

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));

        when(vaccination.getId()).thenReturn(scheduleId);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccination.getScheduledDate()).thenReturn(scheduledDate);
        when(vaccination.getCompletedDate()).thenReturn(null);
        when(vaccination.getCompleted()).thenReturn(false);
        when(vaccine.getId()).thenReturn(vaccineId);

        // when
        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId,
                        scheduleId,
                        request
                );

        // then
        assertNotNull(response);
        assertEquals(scheduleId, response.vaccinationScheduleId());
        assertEquals(vaccineId, response.vaccineId());
        assertEquals(scheduledDate, response.scheduledDate());
    }
    @Test
    void UPDATE_SCHEDULE_BABY_NOT_FOUND() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        LocalDate.of(2026, 12, 15),
                        "사실 우리집 근처",
                        "그다지 안중요"
                );

        when(babyRepository.existsById(babyId)).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                )
        );

        verify(repository, never()).findById(anyLong());
    }
    @Test
    void UPDATE_SCHEDULE_NOT_FOUND() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                )
        );
    }
    @Test
    void UPDATE_OTHER_BABY_SCHEDULE_FAIL() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(2L);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                )
        );

        verify(vaccination, never())
                .updateSchedule(any(), any(), any(), any());
    }
    @Test
    void UPDATE_VACCINE_NOT_FOUND() {
        Long babyId = 1L;
        Long scheduleId = 10L;
        Long vaccineId = 3L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        vaccineId,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                )
        );

        verify(vaccination, never())
                .updateSchedule(any(), any(), any(), any());
    }
    @Test
    void UPDATE_COMPLETED_SCHEDULE_FAIL() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccination.getCompleted()).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, scheduleId, request
                )
        );

        verify(vaccination, never())
                .updateSchedule(any(), any(), any(), any());
    }
}
