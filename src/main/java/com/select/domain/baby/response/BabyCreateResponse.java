package com.select.domain.baby.response;

import java.time.LocalDate;

public record BabyCreateResponse(
        Long babyId,
        String name,
        LocalDate birthDate
) {
}
