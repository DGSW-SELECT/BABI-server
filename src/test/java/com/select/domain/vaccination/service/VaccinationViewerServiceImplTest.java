package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaccinationViewerServiceImplTest {

    @Mock private BabyVaccinationRepository repository;
    private VaccinationServiceImpl service;
    @Mock private VaccineRepository vaccineRepository;
    @Mock private BabyRepository babyRepository;
    @Mock private BabyAccessRepository babyAccessRepository;
    private Clock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);

        clock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(repository, vaccineRepository, babyRepository, babyAccessRepository, clock);
    }

    private void mockViewerAccess() {
        when(babyRepository.existsById(1L)).thenReturn(true);

        when(babyAccessRepository.hasWriteAccess(1L,1L))
                .thenReturn(false);
    }
    private void mockWriteAccess() {
        when(babyRepository.existsById(1L)).thenReturn(true);

        when(babyAccessRepository.hasWriteAccess(1L, 1L))
                .thenReturn(true);

    }

    @Test
    void VIEWER_CANNOT_CREATE_VACCINATION_SCHEDULE() {
        mockViewerAccess();

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        1L,
                        1,
                        LocalDate.of(2026, 11, 20),
                        null,
                        "병원",
                        "메모"
                );

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(1L, 1L, request)
        );

        verifyNoInteractions(vaccineRepository);
        verify(repository, never())
                .saveAndFlush(any());
    }
    @Test
    void VIEWER_CANNOT_UPDATE_VACCINATION_SCHEDULE() {
        mockViewerAccess();

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        1L,
                        1,
                        LocalDate.of(2026, 11, 20),
                        "병원",
                        "메모"
                );

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(1L, 1L, 1L, request)
        );

        verifyNoInteractions(vaccineRepository);
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void VIEWER_CANNOT_COMPLETE_VACCINATION_SCHEDULE() {
        mockViewerAccess();

        VaccinationScheduleSuccessRequest request =
                new VaccinationScheduleSuccessRequest(true, null);

        assertThrows(
                BusinessException.class,
                () -> service.successVaccinationSchedule(1L, 1L, 1L, request)
        );

        verify(repository, never()).findById(anyLong());
    }
    @Test
    void VIEWER_CANNOT_DELETE_VACCINATION_SCHEDULE() {
        mockViewerAccess();

        assertThrows(
                BusinessException.class,
                () -> service.deleteVaccinationSchedule(1L, 1L, 1L)
        );

        verify(repository, never()).findById(anyLong());
        verify(repository, never()).delete(any());
    }
    @Test
    void REMOVED_MEMBER_CANNOT_READ_VACCINATION_SCHEDULE() {
        when(babyRepository.existsById(1L)).thenReturn(true);

        when(babyAccessRepository.hasAccess(1L, 1L))
                .thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.getVaccinationHistory(1L, 1L)
        );

        verify(repository, never())
                .findAllByBabyIdAndCompletedTrueOrderByCompletedDateDescIdDesc(anyLong());
    }
    @Test
    void REMOVED_MEMBER_CANNOT_CREATE_VACCINATION_SCHEDULE() {
        when(babyRepository.existsById(1L)).thenReturn(true);

        when(babyAccessRepository.hasWriteAccess(1L, 1L))
                .thenReturn(false);

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        1L,
                        1,
                        LocalDate.of(2026, 11, 20),
                        null,
                        "병원",
                        "메모"
                );

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(1L, 1L, request)
        );

        verify(repository, never()).saveAndFlush(any());
    }
    @Test
    void CREATE_VACCINATION_SCHEDULE_DB_DUPLICATE() {
        mockWriteAccess();

        Vaccine vaccine = mock(Vaccine.class);

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(vaccineRepository.findById(1L))
                .thenReturn(Optional.of(vaccine));

        when(vaccine.getId()).thenReturn(1L);
        when(vaccine.getDoseNumber()).thenReturn(1);

        when(repository
                .existsByBabyIdAndVaccine_Id(1L, 1L))
                .thenReturn(false);

        org.hibernate.exception.ConstraintViolationException cause =
                new org.hibernate.exception.ConstraintViolationException(
                        "duplicate",
                        new java.sql.SQLException("duplicate"),
                        "uk_baby_vaccine"
                );

        doThrow(new DataIntegrityViolationException("duplicate", cause))
                .when(repository)
                .saveAndFlush(any(BabyVaccination.class));

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        1L,
                        1,
                        LocalDate.of(2026, 11, 20),
                        null,
                        "병원",
                        "메모"
                );

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(1L, 1L, request)
        );
    }
    @Test
    void ALREADY_COMPLETED_VACCINATION_DATE_NOT_CHANGED() {
        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        BabyVaccination vaccination = mock(BabyVaccination.class);

        when(repository.findById(1L))
                .thenReturn(Optional.of(vaccination));

        when(vaccination.getBabyId()).thenReturn(1L);
        when(vaccination.getCompleted()).thenReturn(true);

        when(vaccination.getId()).thenReturn(1L);
        when(vaccination.getVaccine()).thenReturn(mock(Vaccine.class));
        when(vaccination.getScheduledDate()).thenReturn(
                LocalDate.now(clock)
        );

        LocalDate completedDate = LocalDate.of(2026, 10, 15);

        when(vaccination.getCompletedDate())
                .thenReturn(completedDate);

        VaccinationScheduleSuccessRequest request =
                new VaccinationScheduleSuccessRequest(true, null);

        service.successVaccinationSchedule(1L, 1L, 1L, request);

        verify(vaccination).complete(completedDate);
    }
}