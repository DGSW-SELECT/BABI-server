package com.select.domain.growth.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GrowthRecordCreateRequest(

        @NotNull
        @PastOrPresent(message = "측정일은 오늘 또는 과거 날짜만 가능합니다.")
        LocalDate measuredDate,

        @DecimalMin("0.01")
        @Digits(integer = 3, fraction = 2)
        BigDecimal heightCm,

        @DecimalMin("0.01")
        @Digits(integer = 3, fraction = 2)
        BigDecimal weightKg,

        @Size(max = 255)
        String memo
) {
}
