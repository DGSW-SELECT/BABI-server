package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationTransactionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 1);

    private static final Long BABY_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Long VACCINE_ID = 3L;

    private BabyVaccinationRepository babyVaccinationRepository;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private VaccinationTransactionService service;

    private Baby baby;
    private Vaccine vaccine;

    @BeforeEach
    void setUp() {
        babyVaccinationRepository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);

        Clock clock = Clock.fixed(
                Instant.parse("2026-11-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        // @RequiredArgsConstructor 필드 선언 순서와 동일
        service = new VaccinationTransactionService(
                babyVaccinationRepository,
                vaccineRepository,
                babyRepository,
                babyAccessRepository,
                clock
        );

        baby = mock(Baby.class);
        when(baby.getBirthDate()).thenReturn(BIRTH_DATE);

        vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(VACCINE_ID);
        when(vaccine.getName()).thenReturn("BCG");
        when(vaccine.getDoseNumber()).thenReturn(1);
    }

    // ---------- helpers ----------

    /** lockBabyAndValidateWriteAccess 통과 조건 */
    private void givenWriteAccess() {
        when(babyRepository.findByIdForUpdate(BABY_ID)).thenReturn(Optional.of(baby));
        when(babyAccessRepository.findMemberId(
                USER_ID, BABY_ID, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.of(100L));
    }

    private BabyVaccination givenSchedule() {
        BabyVaccination vaccination = BabyVaccination.create(
                BABY_ID, vaccine, TODAY, null, "기존 병원", "기존 메모"
        );
        when(babyVaccinationRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(vaccination));
        return vaccination;
    }

    private void givenVaccine() {
        when(vaccineRepository.findById(VACCINE_ID)).thenReturn(Optional.of(vaccine));
    }

    private VaccinationScheduleUpdateRequest request(
            LocalDate date, String institution, String memo
    ) {
        return new VaccinationScheduleUpdateRequest(VACCINE_ID, 1, date, institution, memo);
    }

    private void assertErrorCode(ErrorCode expected, Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        // BusinessException 의 getter 이름이 다르면 여기만 수정
        assertEquals(expected, ex.getErrorCode());
    }

    // ---------- 성공 ----------

    @Test
    void UPDATE_TO_PAST_DATE_SUCCESS() {
        LocalDate pastDate = TODAY.minusDays(5);
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule();
        givenVaccine();

        var response = service.updateVaccinationSchedule(
                BABY_ID, USER_ID, SCHEDULE_ID, request(pastDate, "병원", "과거 일정으로 변경"));

        assertEquals(pastDate, response.scheduledDate());
        assertEquals(pastDate, vaccination.getScheduledDate());
        verify(babyVaccinationRepository).flush();
    }

    @Test
    void UPDATE_TO_FUTURE_DATE_SUCCESS() {
        LocalDate futureDate = TODAY.plusDays(10);
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule();
        givenVaccine();

        var response = service.updateVaccinationSchedule(
                BABY_ID, USER_ID, SCHEDULE_ID, request(futureDate, "병원", "미래 일정으로 변경"));

        assertEquals(futureDate, response.scheduledDate());
        assertEquals(futureDate, vaccination.getScheduledDate());
    }

    @Test
    void UPDATE_MEMO_ONLY_SUCCESS() {
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule();
        givenVaccine();

        var response = service.updateVaccinationSchedule(
                BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "기존 병원", "수정된 메모"));

        assertEquals(TODAY, response.scheduledDate());
        assertEquals("수정된 메모", response.memo());
        assertEquals(TODAY, vaccination.getScheduledDate());
    }

    // ---------- 권한 / 존재 ----------

    @Test
    void UPDATE_BABY_NOT_FOUND() {
        when(babyRepository.findByIdForUpdate(BABY_ID)).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.BABY_NOT_FOUND, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));

        verifyNoInteractions(babyVaccinationRepository);
    }

    @Test
    void UPDATE_ACCESS_DENIED() {
        when(babyRepository.findByIdForUpdate(BABY_ID)).thenReturn(Optional.of(baby));
        when(babyAccessRepository.findMemberId(
                USER_ID, BABY_ID, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.ACCESS_DENIED, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));

        verifyNoInteractions(babyVaccinationRepository);
    }

    @Test
    void UPDATE_SCHEDULE_NOT_FOUND() {
        givenWriteAccess();
        when(babyVaccinationRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));
    }

    @Test
    void UPDATE_OTHER_BABY_SCHEDULE_FAIL() {
        givenWriteAccess();
        // 다른 아기(2L)의 일정
        BabyVaccination other = BabyVaccination.create(2L, vaccine, TODAY, null, null, null);
        when(babyVaccinationRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(other));

        // 다른 아기 일정은 존재 여부를 숨기려고 NOT_FOUND 를 던짐 (ACCESS_DENIED 아님)
        assertErrorCode(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));
    }

    // ---------- 도메인 규칙 ----------

    @Test
    void UPDATE_COMPLETED_SCHEDULE_FAIL() {
        givenWriteAccess();
        BabyVaccination completed = mock(BabyVaccination.class);
        when(completed.getBabyId()).thenReturn(BABY_ID);
        when(completed.getCompleted()).thenReturn(true);
        when(babyVaccinationRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(completed));

        assertErrorCode(ErrorCode.VACCINATION_ALREADY_COMPLETED, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));

        verify(completed, never()).updateSchedule(any(), any(), any(), any());
    }

    @Test
    void UPDATE_VACCINE_NOT_FOUND() {
        givenWriteAccess();
        givenSchedule();
        when(vaccineRepository.findById(VACCINE_ID)).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.VACCINE_NOT_FOUND, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));
    }

    @Test
    void UPDATE_DOSE_NUMBER_MISMATCH() {
        givenWriteAccess();
        givenSchedule();
        givenVaccine(); // vaccine.getDoseNumber() == 1

        var request = new VaccinationScheduleUpdateRequest(VACCINE_ID, 2, TODAY, "", "");

        assertErrorCode(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER, () ->
                service.updateVaccinationSchedule(BABY_ID, USER_ID, SCHEDULE_ID, request));
    }

    @Test
    void UPDATE_DATE_BEFORE_BIRTH_FAIL() {
        givenWriteAccess();
        givenSchedule();
        givenVaccine();

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID,
                        request(BIRTH_DATE.minusDays(1), "", "")));
    }

    @Test
    void UPDATE_DATE_NULL_FAIL() {
        givenWriteAccess();
        givenSchedule();
        givenVaccine();

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(null, "", "")));
    }

    @Test
    void UPDATE_DUPLICATE_VACCINATION_FAIL() {
        givenWriteAccess();
        givenSchedule();
        givenVaccine();
        when(babyVaccinationRepository.existsByBabyIdAndVaccine_IdAndIdNot(
                BABY_ID, VACCINE_ID, SCHEDULE_ID
        )).thenReturn(true);

        assertErrorCode(ErrorCode.DUPLICATE_VACCINATION, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));
    }

    @Test
    void UPDATE_DUPLICATE_CONSTRAINT_ON_FLUSH_FAIL() {
        givenWriteAccess();
        givenSchedule();
        givenVaccine();

        var cause = new ConstraintViolationException(
                "duplicate", new SQLException(), "uk_baby_vaccine");
        doThrow(new DataIntegrityViolationException("dup", cause))
                .when(babyVaccinationRepository).flush();

        assertErrorCode(ErrorCode.DUPLICATE_VACCINATION, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));
    }

    @Test
    void UPDATE_OTHER_CONSTRAINT_ON_FLUSH_IS_RETHROWN() {
        givenWriteAccess();
        givenSchedule();
        givenVaccine();

        var cause = new ConstraintViolationException(
                "other", new SQLException(), "some_other_constraint");
        doThrow(new DataIntegrityViolationException("other", cause))
                .when(babyVaccinationRepository).flush();

        assertThrows(DataIntegrityViolationException.class, () ->
                service.updateVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID, request(TODAY, "", "")));
    }
}