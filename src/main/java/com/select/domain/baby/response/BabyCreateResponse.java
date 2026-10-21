package com.select.domain.baby.response;

import java.time.LocalDate;

public record BabyCreateResponse(
        Long id,
        String name,
        LocalDate birthDate
) {
}
