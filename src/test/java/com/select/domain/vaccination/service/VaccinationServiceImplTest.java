package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationServiceImplTest{
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
                TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(repository, vaccineRepository, babyRepository, babyAccessRepository, clock);
    }

    private void mockViewerAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);

        when(babyAccessRepository.hasAccess(userId, babyId))
                .thenReturn(true);

        when(babyAccessRepository.hasWriteAccess(userId, babyId)).thenReturn(false);
    }
    private void mockWriteAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);

        when(babyAccessRepository.hasAccess(userId, babyId))
                .thenReturn(true);

        when(babyAccessRepository.hasWriteAccess(userId, babyId))
                .thenReturn(true);
    }

    @Test
    // 정상적인 요청이면 예방접종 일정 반환
    void GOOD_OFFER_VACCINE_SCHEDULE_CHANGE() {
        LocalDate from = LocalDate.of(2026,1,1);
        LocalDate to = LocalDate.of(2026,12,31);

        Vaccine vaccine = mock(Vaccine.class);
        BabyVaccination vaccination = mock(BabyVaccination.class);

        when(vaccination.getId()).thenReturn(10L);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccine.getId()).thenReturn(20L);
        when(vaccine.getName()).thenReturn("B형간염");
        when(vaccine.getRecommendedStartMonth()).thenReturn(0);
        when(vaccine.getRecommendedEndMonth()).thenReturn(1);
        when(vaccination.getScheduledDate()).thenReturn(LocalDate.of(2026, 2,1));
        when(vaccination.getCompletedDate()).thenReturn(null);
        when(vaccination.getCompleted()).thenReturn(false);

        when(repository
                .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                    babyId, from, to))
                .thenReturn(List.of(vaccination));
        mockViewerAccess();
        // when
        VaccinationScheduleResponse response =
                service.getVaccinationSchedules(babyId, userId, from, to);

        // then
        assertEquals(babyId, response.babyId());
        assertEquals(1, response.vaccinationSchedules().size());

        // verify
        verify(repository)
                .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                        babyId, from, to
                );
    }

    @Test
    // 스케줄이 없으면 빈 목록 반환
    void NO_SCHEDULE_RETURN_EMPTY_LIST() {
        LocalDate from = LocalDate.of(2026,1,1);
        LocalDate to = LocalDate.of(2026,12,31);

        when(repository
                .findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                        babyId, from, to
                ))
                .thenReturn(List.of());
        mockViewerAccess();
        // when
        VaccinationScheduleResponse response =
                service.getVaccinationSchedules(babyId, userId, from, to);

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
                        10L,
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
                        10L,
                        LocalDate.of(2026,1,1),
                        LocalDate.of(2027,1,3)
                )
        );
        verifyNoInteractions(repository);
    }
    @Test
// 정상 등록
    void CREATE_VACCINATION_SCHEDULE_SUCCESS() {
        Long vaccineId = 3L;

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        vaccineId,
                        1,
                        TODAY.plusDays(1),
                        null,
                        "",
                        ""
                );

        Vaccine vaccine = mock(Vaccine.class);

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));

        when(vaccine.getId()).thenReturn(vaccineId);
        when(vaccine.getName()).thenReturn("B형간염");
        when(vaccine.getDoseNumber()).thenReturn(1);

        when(repository.existsByBabyIdAndVaccine_Id(babyId, vaccineId))
                .thenReturn(false);

        when(repository.saveAndFlush(any(BabyVaccination.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var response = service.createVaccinationSchedule(babyId, userId, request);

        // then
        assertNotNull(response);
        verify(repository).saveAndFlush(any(BabyVaccination.class));
    }

    @Test
    // 존재하지 않는 아기
    void CREATE_VACCINATION_SCHEDULE_BABY_NOT_FOUND() {
        Long babyId = 999L;

        var request = new VaccinationScheduleCreateRequest(
                3L, 1, TODAY.plusDays(1),null,"",""
        );

        when(babyRepository.existsById(babyId)).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
// 존재하지 않는 백신
    void CREATE_VACCINATION_SCHEDULE_VACCINE_NOT_FOUND() {
        Long vaccineId = 999L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId, 1, TODAY.plusDays(1),null,"",""
        );

        mockWriteAccess();
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
    // 오늘 날짜는 허용하지 않음
    void CREATE_VACCINATION_SCHEDULE_INVALID_DATE() {
        Long vaccineId = 3L;

        VaccinationScheduleCreateRequest request =
                new VaccinationScheduleCreateRequest(
                        vaccineId,
                        1,
                        TODAY,
                        null,
                        "",
                        ""
                );

        mockWriteAccess();

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }

    @Test
// 중복 등록
    void CREATE_VACCINATION_SCHEDULE_DUPLICATE() {
        Long vaccineId = 3L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId, 1, TODAY.plusDays(1),null,"",""
        );

        Vaccine vaccine = mock(Vaccine.class);

        mockWriteAccess();
        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getId()).thenReturn(vaccineId);
        when(vaccine.getDoseNumber()).thenReturn(1);

        when(repository.existsByBabyIdAndVaccine_Id(babyId, vaccineId))
                .thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }
    @Test
        // 접종 완료 처리: 완료 여부가 true이고 완료일이 지정됨
    void UPDATE_VACCINATION_SCHEDULE_COMPLETE_SUCCESS() {
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(3L);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                TODAY,
                null,
                null,
                null
        );

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(true, LocalDate.of(2026,11,10));

        // when
        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(
                        babyId, userId ,scheduleId, request
                );

        // then
        assertTrue(response.completed());
        assertEquals(TODAY, response.completedDate());
        assertTrue(vaccination.getCompleted());
    }

    @Test
        // 접종 완료 취소: 완료 여부가 false이고 완료일이 제거됨
    void UPDATE_VACCINATION_SCHEDULE_CANCEL_COMPLETE_SUCCESS() {
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(3L);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                TODAY.plusDays(7),
                null,
                null,
                null
        );
        vaccination.complete(TODAY);

        mockWriteAccess();
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(false, null);

        // when
        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        // then
        assertFalse(response.completed());
        assertNull(response.completedDate());
        assertFalse(vaccination.getCompleted());
    }

    @Test
        // 접종 일정이 존재하지 않으면 예외 발생
    void UPDATE_VACCINATION_SCHEDULE_NOT_FOUND() {
        Long scheduleId = 999L;

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.empty());

        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertThrows(
                BusinessException.class,
                () -> service.successVaccinationSchedule(
                        babyId, userId, scheduleId, request
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
                TODAY.plusDays(7),
                null,
                null,
                null
        );

        when(babyRepository.existsById(requestedBabyId))
                .thenReturn(true);
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertThrows(
                BusinessException.class,
                () -> service.successVaccinationSchedule(
                        requestedBabyId, userId, scheduleId, request
                )
        );

        assertFalse(vaccination.getCompleted());
        assertNull(vaccination.getCompletedDate());
    }
    @Test
    // 아기의 구성원이 아니면 예방접종 일정을 생성할 수 없음
    void CREATE_VACCINATION_SCHEDULE_ACCESS_DENIED() {
        var request = new VaccinationScheduleCreateRequest(
                3L, 1, TODAY.plusDays(1), null, "", ""
        );

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.hasAccess(userId, babyId))
                .thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(vaccineRepository, never()).findById(anyLong());
        verify(repository, never()).save(any(BabyVaccination.class));
    }
    @Test
// 과거 날짜로 예방접종 일정을 생성할 수 있음
    void CREATE_VACCINATION_SCHEDULE_PAST_DATE_SUCCESS() {
        Long vaccineId = 3L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId,
                1,
                TODAY.minusDays(1),
                null,
                "",
                ""
        );

        Vaccine vaccine = mock(Vaccine.class);
        BabyVaccination saved = mock(BabyVaccination.class);

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getDoseNumber()).thenReturn(1);
        when(repository.saveAndFlush(any(BabyVaccination.class)))
                .thenReturn(saved);
        when(saved.getId()).thenReturn(1L);

        assertDoesNotThrow(
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository).saveAndFlush(any(BabyVaccination.class));
    }
    @Test
    // 백신의 접종 차수와 요청한 차수가 다르면 등록할 수 없음
    void CREATE_VACCINATION_SCHEDULE_DOSE_MISMATCH() {
        Long vaccineId = 3L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId, 2, TODAY.plusDays(1), null, "", ""
        );

        Vaccine vaccine = mock(Vaccine.class);

        mockWriteAccess();

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getDoseNumber()).thenReturn(1);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).save(any(BabyVaccination.class));
    }
    @Test
// 이미 완료된 예방접종 일정은 수정할 수 없음
    void UPDATE_VACCINATION_SCHEDULE_ALREADY_COMPLETED() {
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                TODAY.plusDays(7),
                null,
                null,
                null
        );

        vaccination.complete(TODAY);

        mockWriteAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        assertThrows(
                BusinessException.class,
                () -> service.updateVaccinationSchedule(
                        babyId, userId, scheduleId, null
                )
        );

        assertTrue(vaccination.getCompleted());
    }
    @Test
// 과거 완료일을 지정하여 예방접종 기록을 생성할 수 있음
    void CREATE_VACCINATION_WITH_PAST_COMPLETED_DATE_SUCCESS() {
        Long vaccineId = 3L;
        LocalDate completedDate = TODAY.minusDays(5);

        var request = new VaccinationScheduleCreateRequest(
                vaccineId,
                1,
                null,
                completedDate,
                "병원",
                "과거 접종 기록"
        );

        Vaccine vaccine = mock(Vaccine.class);
        BabyVaccination saved = mock(BabyVaccination.class);

        mockWriteAccess();

        Baby baby = mock(Baby.class);

        when(babyRepository.findById(1L))
                .thenReturn(Optional.of(baby));

        when(baby.getBirthDate())
                .thenReturn(LocalDate.of(2025, 1, 1));

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getDoseNumber()).thenReturn(1);
        when(repository.saveAndFlush(any(BabyVaccination.class)))
                .thenReturn(saved);
        when(saved.getId()).thenReturn(10L);

        service.createVaccinationSchedule(babyId, userId, request);

        ArgumentCaptor<BabyVaccination> captor =
                ArgumentCaptor.forClass(BabyVaccination.class);

        verify(repository).saveAndFlush(captor.capture());

        BabyVaccination created = captor.getValue();

        assertTrue(created.getCompleted());
        assertEquals(completedDate, created.getCompletedDate());
    }
    @Test
// 미래 완료일로 예방접종 기록을 생성할 수 없음
    void CREATE_VACCINATION_WITH_FUTURE_COMPLETED_DATE_FAIL() {
        Long vaccineId = 3L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId,
                1,
                null,
                TODAY.plusDays(1),
                "병원",
                "메모"
        );

        Vaccine vaccine = mock(Vaccine.class);

        mockWriteAccess();

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getDoseNumber()).thenReturn(1);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).saveAndFlush(any(BabyVaccination.class));
    }
    @Test
// 예정일과 완료일이 모두 없으면 예방접종 기록을 생성할 수 없음
    void CREATE_VACCINATION_WITHOUT_ANY_DATE_FAIL() {
        Long vaccineId = 3L;

        var request = new VaccinationScheduleCreateRequest(
                vaccineId,
                1,
                null,
                null,
                "병원",
                "메모"
        );

        Vaccine vaccine = mock(Vaccine.class);

        mockWriteAccess();

        when(vaccineRepository.findById(vaccineId))
                .thenReturn(Optional.of(vaccine));
        when(vaccine.getDoseNumber()).thenReturn(1);

        assertThrows(
                BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, request)
        );

        verify(repository, never()).saveAndFlush(any(BabyVaccination.class));
    }
}
