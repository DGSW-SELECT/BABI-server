package com.select.domain.growth.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Date;

public record GrowthRecordCreateRequest(

        @NotNull
        Date measuredDate,

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
