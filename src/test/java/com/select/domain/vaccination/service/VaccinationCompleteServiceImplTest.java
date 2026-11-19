package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VaccinationTransactionService.successVaccinationSchedule (접종 완료 처리 / 취소) 테스트.
 * 실제 VaccinationTransactionService 객체를 사용하고, repository / Baby / Vaccine 만 mock 이다.
 */
class VaccinationCompleteServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 1);

    private final Long babyId = 1L;
    private final Long userId = 10L;
    private final Long scheduleId = 10L;

    private BabyVaccinationRepository repository;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private VaccinationTransactionService service;

    private Baby baby;
    private Vaccine vaccine;

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);

        // LocalDate.now(clock) == 2026-11-10
        Clock clock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationTransactionService(
                repository, vaccineRepository, babyRepository, babyAccessRepository, clock);

        baby = mock(Baby.class);
        when(baby.getBirthDate()).thenReturn(BIRTH_DATE);

        vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(3L);
        when(vaccine.getName()).thenReturn("BCG");
        when(vaccine.getDoseNumber()).thenReturn(1);
    }

    // ---------- helpers ----------

    /** lockBabyAndValidateWriteAccess 통과 조건 (아기 행 잠금 + 쓰기 권한) */
    private void mockWriteAccess() {
        when(babyRepository.findByIdForUpdate(babyId)).thenReturn(Optional.of(baby));
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.of(100L));
    }

    /** 예정일 TODAY, 미완료 상태의 일정을 만들고 findById 에 연결 */
    private BabyVaccination givenSchedule(Long ownerBabyId) {
        BabyVaccination vaccination = BabyVaccination.create(
                ownerBabyId, vaccine, TODAY, null, null, null);
        when(repository.findById(scheduleId)).thenReturn(Optional.of(vaccination));
        return vaccination;
    }

    private boolean isCompleted(BabyVaccination vaccination) {
        return Boolean.TRUE.equals(vaccination.getCompleted());
    }

    private void assertErrorCode(ErrorCode expected, Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        // BusinessException 의 getter 이름이 다르면 이 한 줄만 수정
        assertEquals(expected, ex.getErrorCode());
    }

    // =========================== 완료 처리 ===========================

    @Test
        // 완료일을 지정하지 않으면 오늘 날짜로 완료 처리
    void COMPLETE_VACCINATION_WITHOUT_DATE_SUCCESS() {
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);

        var request = new VaccinationScheduleSuccessRequest(true, null);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertTrue(response.completed());
        assertEquals(TODAY, response.completedDate());
        assertTrue(isCompleted(vaccination));
        assertEquals(TODAY, vaccination.getCompletedDate());
    }

    @Test
        // 과거 날짜를 지정하면 그 날짜로 완료 처리
    void COMPLETE_VACCINATION_WITH_PAST_DATE_SUCCESS() {
        LocalDate pastDate = TODAY.minusDays(3);
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);

        var request = new VaccinationScheduleSuccessRequest(true, pastDate);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertTrue(response.completed());
        assertEquals(pastDate, response.completedDate());
        assertEquals(pastDate, vaccination.getCompletedDate());
    }

    @Test
        // 미래 날짜로 접종 완료 처리 불가
    void COMPLETE_VACCINATION_WITH_FUTURE_DATE_FAIL() {
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);

        var request = new VaccinationScheduleSuccessRequest(true, TODAY.plusDays(1));

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.successVaccinationSchedule(babyId, userId, scheduleId, request));

        assertFalse(isCompleted(vaccination));
    }

    @Test
        // 아기 생일 이전 날짜로 접종 완료 처리 불가
    void COMPLETE_VACCINATION_BEFORE_BIRTH_FAIL() {
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);

        var request = new VaccinationScheduleSuccessRequest(true, BIRTH_DATE.minusDays(1));

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.successVaccinationSchedule(babyId, userId, scheduleId, request));

        assertFalse(isCompleted(vaccination));
    }

    @Test
        // 이미 완료된 접종을 날짜 없이 다시 완료 처리하면 기존 완료일 유지
    void ALREADY_COMPLETED_VACCINATION_DATE_NOT_CHANGED() {
        LocalDate originalCompletedDate = TODAY.minusDays(2);
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);
        vaccination.complete(originalCompletedDate);

        // 완료일을 보내지 않아야 "기존 완료일 유지" 분기를 탐
        var request = new VaccinationScheduleSuccessRequest(true, null);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertTrue(response.completed());
        assertEquals(originalCompletedDate, response.completedDate());
        assertEquals(originalCompletedDate, vaccination.getCompletedDate());
    }

    @Test
        // 이미 완료된 접종에 새 완료일을 보내면 그 날짜로 덮어씀 (현재 코드의 동작)
    void ALREADY_COMPLETED_VACCINATION_WITH_NEW_DATE_OVERWRITES() {
        LocalDate originalCompletedDate = TODAY.minusDays(5);
        LocalDate newCompletedDate = TODAY.minusDays(1);
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);
        vaccination.complete(originalCompletedDate);

        var request = new VaccinationScheduleSuccessRequest(true, newCompletedDate);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertEquals(newCompletedDate, response.completedDate());
        assertEquals(newCompletedDate, vaccination.getCompletedDate());
    }

    // =========================== 완료 취소 ===========================

    @Test
        // 완료 취소 시 완료 여부와 완료일 초기화
    void CANCEL_VACCINATION_COMPLETION_SUCCESS() {
        mockWriteAccess();
        BabyVaccination vaccination = givenSchedule(babyId);
        vaccination.complete(TODAY.minusDays(1)); // 먼저 완료 상태로 만들어 둠

        var request = new VaccinationScheduleSuccessRequest(false, null);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertFalse(response.completed());
        assertNull(response.completedDate());
        assertFalse(isCompleted(vaccination));
        assertNull(vaccination.getCompletedDate());
    }

    // =========================== 존재 / 권한 ===========================

    @Test
        // 접종 일정이 존재하지 않으면 예외 발생
    void COMPLETE_VACCINATION_SCHEDULE_NOT_FOUND() {
        mockWriteAccess();
        when(repository.findById(scheduleId)).thenReturn(Optional.empty());

        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertErrorCode(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND, () ->
                service.successVaccinationSchedule(babyId, userId, scheduleId, request));
    }

    @Test
        // 다른 아기의 접종 일정은 완료 처리할 수 없음
    void COMPLETE_OTHER_BABY_SCHEDULE_FAIL() {
        mockWriteAccess();
        BabyVaccination other = givenSchedule(2L); // 다른 아기 소유 일정

        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertErrorCode(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND, () ->
                service.successVaccinationSchedule(babyId, userId, scheduleId, request));

        assertFalse(isCompleted(other));
        assertNull(other.getCompletedDate());
    }

    @Test
        // 존재하지 않는 아기
    void COMPLETE_BABY_NOT_FOUND() {
        when(babyRepository.findByIdForUpdate(babyId)).thenReturn(Optional.empty());

        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertErrorCode(ErrorCode.BABY_NOT_FOUND, () ->
                service.successVaccinationSchedule(babyId, userId, scheduleId, request));

        verifyNoInteractions(repository);
    }

    @Test
        // 쓰기 권한이 없으면 완료 처리 불가
    void COMPLETE_ACCESS_DENIED() {
        when(babyRepository.findByIdForUpdate(babyId)).thenReturn(Optional.of(baby));
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.empty());

        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertErrorCode(ErrorCode.ACCESS_DENIED, () ->
                service.successVaccinationSchedule(babyId, userId, scheduleId, request));

        verifyNoInteractions(repository);
    }

    @Test
        // 잘못된 아기 ID
    void COMPLETE_INVALID_BABY_ID() {
        var request = new VaccinationScheduleSuccessRequest(true, null);

        assertErrorCode(ErrorCode.VACCINE_INVALID_BABY_ID, () ->
                service.successVaccinationSchedule(0L, userId, scheduleId, request));

        verifyNoInteractions(babyRepository, repository);
    }
}
