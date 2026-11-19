package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VaccinationTransactionService {

    private final BabyVaccinationRepository babyVaccinationRepository;
    private final VaccineRepository vaccineRepository;
    private final BabyRepository babyRepository;
    private final BabyAccessRepository babyAccessRepository;
    private final Clock clock;

    // 아기 행 잠금 + 트랜잭션 내부 권한 재검사
    private Baby lockBabyAndValidateWriteAccess(Long babyId, Long userId) {
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_BABY_ID);
        }

        Baby baby = babyRepository.findByIdForUpdate(babyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BABY_NOT_FOUND));

        if (babyAccessRepository.findMemberId(
                userId,
                babyId,
                BabyPermission.VACCINATION_WRITE,
                true
        ).isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return baby;
    }

    private boolean isDuplicateVaccinationConstraint(Throwable e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof
                    org.hibernate.exception.ConstraintViolationException ex) {
                return "uk_baby_vaccine".equals(ex.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }

    // 접종 일정 생성
    @Transactional
    public VaccinationScheduleCreateResponse createVaccinationSchedule(
            Long babyId,
            Long userId,
            VaccinationScheduleCreateRequest request
    ) {
        Baby baby = lockBabyAndValidateWriteAccess(babyId, userId);

        Vaccine vaccine = vaccineRepository.findById(request.vaccineId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VACCINE_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        LocalDate scheduledDate = request.scheduledDate();
        LocalDate completedDate = request.completedDate();

        if (!Objects.equals(vaccine.getDoseNumber(), request.doseNumber())) {
            throw new BusinessException(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER);
        }

        if (scheduledDate == null && completedDate == null) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }

        if (completedDate != null && completedDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }

        if (completedDate != null
                && completedDate.isBefore(baby.getBirthDate())) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }

        if (scheduledDate != null
                && scheduledDate.isBefore(baby.getBirthDate())) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }

        if (babyVaccinationRepository.existsByBabyIdAndVaccine_Id(
                babyId, vaccine.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
        }

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                scheduledDate,
                completedDate,
                request.institution(),
                request.memo()
        );

        BabyVaccination saved;

        try {
            saved = babyVaccinationRepository.saveAndFlush(vaccination);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateVaccinationConstraint(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
            }
            throw e;
        }

        return new VaccinationScheduleCreateResponse(
                saved.getId(),
                babyId,
                vaccine.getId(),
                vaccine.getName(),
                vaccine.getDoseNumber(),
                saved.getScheduledDate(),
                saved.getCompletedDate(),
                saved.getInstitution(),
                saved.getMemo(),
                saved.getCompleted()
        );
    }

    // 접종 완료 처리
    @Transactional
    public VaccinationScheduleUpdateResponse successVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleSuccessRequest request
    ) {
        Baby baby = lockBabyAndValidateWriteAccess(babyId, userId);

        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND)
                );

        if (!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND);
        }

        if (request.completed()) {
            LocalDate completedDate =
                    request.completedDate() != null
                            ? request.completedDate()
                            : Boolean.TRUE.equals(vaccination.getCompleted())
                            ? vaccination.getCompletedDate()
                            : LocalDate.now(clock);

            LocalDate today = LocalDate.now(clock);

            if (completedDate == null || completedDate.isAfter(today)) {
                throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
            }

            if (completedDate.isBefore(baby.getBirthDate())) {
                throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
            }

            vaccination.complete(completedDate);
        } else {
            vaccination.cancelComplete();
        }

        return new VaccinationScheduleUpdateResponse(
                vaccination.getId(),
                vaccination.getBabyId(),
                vaccination.getVaccine().getId(),
                vaccination.getVaccine().getName(),
                vaccination.getVaccine().getDoseNumber(),
                vaccination.getScheduledDate(),
                vaccination.getCompletedDate(),
                vaccination.getInstitution(),
                vaccination.getMemo(),
                Boolean.TRUE.equals(vaccination.getCompleted())
        );
    }

    // 접종 일정 수정
    @Transactional
    public VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    ) {
        Baby baby = lockBabyAndValidateWriteAccess(babyId, userId);

        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND)
                );

        if (!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(vaccination.getCompleted())) {
            throw new BusinessException(ErrorCode.VACCINATION_ALREADY_COMPLETED);
        }

        Vaccine vaccine = vaccineRepository.findById(request.vaccineId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.VACCINE_NOT_FOUND)
                );

        if (request.doseNumber() == null || request.doseNumber() < 1) {
            throw new BusinessException(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER);
        }

        if (!Objects.equals(vaccine.getDoseNumber(), request.doseNumber())) {
            throw new BusinessException(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER);
        }

        if (request.scheduledDate() == null
                || request.scheduledDate().isBefore(baby.getBirthDate())) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }

        boolean duplicated =
                babyVaccinationRepository.existsByBabyIdAndVaccine_IdAndIdNot(
                        babyId,
                        request.vaccineId(),
                        vaccinationScheduleId
                );

        if (duplicated) {
            throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
        }

        vaccination.updateSchedule(
                vaccine,
                request.scheduledDate(),
                request.institution(),
                request.memo()
        );

        try {
            babyVaccinationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateVaccinationConstraint(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
            }
            throw e;
        }

        return new VaccinationScheduleUpdateResponse(
                vaccination.getId(),
                vaccination.getBabyId(),
                vaccination.getVaccine().getId(),
                vaccination.getVaccine().getName(),
                vaccination.getVaccine().getDoseNumber(),
                vaccination.getScheduledDate(),
                vaccination.getCompletedDate(),
                vaccination.getInstitution(),
                vaccination.getMemo(),
                vaccination.getCompleted()
        );
    }

    // 접종 일정 삭제
    @Transactional
    public void deleteVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId
    ) {
        lockBabyAndValidateWriteAccess(babyId, userId);

        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND
                        )
                );

        if (!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND);
        }

        babyVaccinationRepository.delete(vaccination);
    }
}
