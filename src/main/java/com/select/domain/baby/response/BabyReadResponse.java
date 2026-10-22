package com.select.domain.baby.response;

import java.time.LocalDate;

public record BabyReadResponse(
        Long babyId,
        String name,
        LocalDate birthDate
) {
}
