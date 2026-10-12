package com.select.domain.vaccination.service;

import com.select.domain.vaccination.dto.response.VaccinationScheduleItemResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VaccinationServiceImpl implements VaccinationService {

    private static final long MAX_RANGE_DAY = 366;

    private final BabyVaccinationRepository babyVaccinationRepository;

    @Override
    public VaccinationScheduleResponse getVaccinationSchedules(
            Long babyId,
            LocalDate from,
            LocalDate to
    ) {
        // 1. 입력값 검증 부분
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_BABY_ID
            );
        }
        if(from == null || to == null || from.isAfter(to)){
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE
            );
        }

        long rangeDays = ChronoUnit.DAYS.between(from, to);

        if (rangeDays > MAX_RANGE_DAY) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_SET
            );
        }

        // 2. 아기별 접근 권 검증
        // TODO: 나중에 로그인 사용 babyId 접근 권한 확인
        // 이를 위해 인증 정 및 권한 부여 서비스 필요

        // 3. 일정 조회
        List<BabyVaccination> babyVaccinations =
                babyVaccinationRepository.findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                        babyId,
                        from,
                        to
                );
        // 4. Entity -> DTO변환
        List<VaccinationScheduleItemResponse> schedules =
                babyVaccinations.stream().map(babyVaccination -> {
                    Vaccine vaccine = babyVaccination.getVaccine();
                    return new VaccinationScheduleItemResponse(
                            babyVaccination.getId(),
                            vaccine.getId(),
                            vaccine.getName(),
                            vaccine.getDoseNumber(),
                            vaccine.getRecommendedStartMonth(),
                            vaccine.getRecommendedEndMonth(),
                            babyVaccination.getScheduledDate(),
                            babyVaccination.getCompletedDate(),
                            babyVaccination.getCompleted()
                    );
                }).toList();

        // 5. 응답 출력
        return new VaccinationScheduleResponse(
                babyId,
                schedules
        );
    }

}
