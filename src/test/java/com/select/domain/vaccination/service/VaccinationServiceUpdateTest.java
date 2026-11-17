package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyMemberRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationServiceUpdateTest {
    private BabyVaccinationRepository repository;
    private VaccinationServiceImpl service;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private Clock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final Long babyId = 1L;
    private final Long userId = 10L;

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);

        clock = Clock.fixed(
                Instant.parse("2026-11-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(repository, vaccineRepository, babyRepository, babyAccessRepository, clock);
    }
    private void mockWriteAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);

        when(babyAccessRepository.hasAccess(userId, babyId))
                .thenReturn(true);

        when(babyAccessRepository.hasWriteAccess(userId, babyId))
                .thenReturn(true);
    }
    @Test
    void UPDATE_VACCINATION_SCHEDULE_SUCCESS() {
        // given
        Long scheduleId = 10L;
        Long vaccineId = 3L;

        LocalDate scheduledDate = LocalDate.of(2026, 12, 15);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        vaccineId,
                        1,
                        scheduledDate,
                        "어디든지",
                        "많이 아픔"
                );

        BabyVaccination vaccination = mock(BabyVaccination.class);
        Vaccine vaccine = mock(Vaccine.class);

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccination.getCompleted()).thenReturn(false);

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));

        when(vaccination.getId()).thenReturn(scheduleId);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccination.getScheduledDate()).thenReturn(scheduledDate);
        when(vaccine.getId()).thenReturn(vaccineId);
        when(vaccine.getDoseNumber()).thenReturn(1);
        when(vaccination.getCompletedDate()).thenReturn(null);
        when(vaccination.getCompleted()).thenReturn(false);
        when(vaccine.getId()).thenReturn(vaccineId);

        // when
        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId,
                        userId,
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
        Long scheduleId = 10L;

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        1,
                        LocalDate.of(2026, 12, 15),
                        "사실 우리집 근처",
                        "그다지 안중요"
                );

        when(babyRepository.existsById(babyId)).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                )
        );

        verify(repository, never()).findById(anyLong());
    }
    @Test
    void UPDATE_SCHEDULE_NOT_FOUND() {
        Long scheduleId = 10L;

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        1,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        mockWriteAccess();
        when(repository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                )
        );
    }
    @Test
    void UPDATE_OTHER_BABY_SCHEDULE_FAIL() {
        Long scheduleId = 10L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        1,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );
        mockWriteAccess();
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(2L);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                )
        );

        verify(vaccination, never())
                .updateSchedule(any(), any(), any(), any());
    }
    @Test
    void UPDATE_VACCINE_NOT_FOUND() {
        Long scheduleId = 10L;
        Long vaccineId = 3L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        vaccineId,
                        1,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        mockWriteAccess();
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                )
        );

        verify(vaccination, never())
                .updateSchedule(any(), any(), any(), any());
    }
    @Test
    void UPDATE_COMPLETED_SCHEDULE_FAIL() {
        Long scheduleId = 10L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        VaccinationScheduleUpdateRequest request =
                new VaccinationScheduleUpdateRequest(
                        3L,
                        1,
                        LocalDate.of(2026, 12, 15),
                        "",
                        ""
                );

        mockWriteAccess();
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccination.getCompleted()).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                )
        );

        verify(vaccination, never())
                .updateSchedule(any(), any(), any(), any());
    }

    @Test
// 기존 일정을 과거 날짜로 변경할 수 있음
    void UPDATE_VACCINATION_SCHEDULE_TO_PAST_DATE_SUCCESS() {
        Long scheduleId = 10L;
        Long vaccineId = 3L;
        LocalDate pastDate = TODAY.minusDays(5);

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(vaccineId);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId, vaccine, TODAY, null, null, null
        );

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));

        when(vaccine.getDoseNumber()).thenReturn(1);

        var request = new VaccinationScheduleUpdateRequest(
                vaccineId,
                1,
                pastDate,
                "병원",
                "과거 일정으로 변경"
        );

        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        assertEquals(pastDate, response.scheduledDate());
        assertEquals(pastDate, vaccination.getScheduledDate());
    }
    @Test
// 기존 일정을 미래 날짜로 변경할 수 있음
    void UPDATE_VACCINATION_SCHEDULE_TO_FUTURE_DATE_SUCCESS() {
        Long scheduleId = 10L;
        Long vaccineId = 3L;
        LocalDate futureDate = TODAY.plusDays(10);

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(vaccineId);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId, vaccine, TODAY, null, null, null
        );

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));

        when(vaccine.getDoseNumber()).thenReturn(1);

        var request = new VaccinationScheduleUpdateRequest(
                vaccineId,
                1,
                futureDate,
                "병원",
                "미래 일정으로 변경"
        );

        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        assertEquals(futureDate, response.scheduledDate());
        assertEquals(futureDate, vaccination.getScheduledDate());
    }
    // 일정 날짜는 유지하고 메모만 수정할 수 있음
    void UPDATE_VACCINATION_SCHEDULE_MEMO_ONLY_SUCCESS() {
        Long scheduleId = 10L;
        Long vaccineId = 3L;

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(vaccineId);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId, vaccine, TODAY, null, "기존 병원", "기존 메모"
        );

        mockWriteAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleUpdateRequest(
                vaccineId,
                1,
                TODAY,
                "기존 병원",
                "수정된 메모"
        );

        VaccinationScheduleUpdateResponse response =
                service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        assertEquals(TODAY, response.scheduledDate());
        assertEquals("수정된 메모", response.memo());
        assertEquals(TODAY, vaccination.getScheduledDate());
    }
}
