package com.select.domain.handover.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


class HandoverBriefingCreateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validPeriodShouldPassValidation() {
        HandoverBriefingCreateRequest request = createRequest(
                LocalDateTime.of(2026, 10, 20, 9, 0),
                LocalDateTime.of(2026, 10, 20, 18, 0)
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void equalStartAndEndShouldFailValidation() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 10, 20, 9, 0);
        HandoverBriefingCreateRequest request = createRequest(sameTime, sameTime);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void startAfterEndShouldFailValidation() {
        HandoverBriefingCreateRequest request = createRequest(
                LocalDateTime.of(2026, 10, 20, 18, 0),
                LocalDateTime.of(2026, 10, 20, 9, 0)
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void nullValuesShouldFailValidation() {
        HandoverBriefingCreateRequest request = new HandoverBriefingCreateRequest(
                null,
                null,
                null
        );

        var violations = validator.validate(request);

        var invalidFields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(3, violations.size());
        assertEquals(
                java.util.Set.of("briefingDate", "periodStart", "periodEnd"),
                invalidFields
        );
    }

    private HandoverBriefingCreateRequest createRequest(
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    ) {
        return new HandoverBriefingCreateRequest(
                LocalDate.of(2026, 10, 21),
                periodStart,
                periodEnd
        );
    }
}
