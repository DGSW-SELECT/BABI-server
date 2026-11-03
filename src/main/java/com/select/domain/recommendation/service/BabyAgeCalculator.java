package com.select.domain.recommendation.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

public class BabyAgeCalculator {

    public long calculateMonths(LocalDate birthDate, LocalDate baseDate) {
        validateDateOrder(birthDate, baseDate);
        return Period.between(birthDate, baseDate).toTotalMonths();
    }

    public long calculateCompletedMonths(LocalDate birthDate, LocalDate baseDate) {
        validateDateOrder(birthDate, baseDate);
        return ChronoUnit.MONTHS.between(
                birthDate.withDayOfMonth(1),
                baseDate.withDayOfMonth(1)
        );
    }

    private void validateDateOrder(LocalDate birthDate, LocalDate baseDate) {
        if (birthDate.isAfter(baseDate)) {
            throw new IllegalArgumentException("Birth date cannot be after the base date");
        }
    }
}
