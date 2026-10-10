
package com.select.domain.user.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupRequestTest {

    @Test
    void passwordWithExactly8CharactersShouldPassValidation() {
        assertTrue(validatePassword("a".repeat(8)));
    }

    @Test
    void passwordWithExactly20CharactersShouldPassValidation() {
        assertTrue(validatePassword("a".repeat(20)));
    }

    @Test
    void passwordWith7CharactersShouldFailValidation() {
        assertFalse(validatePassword("a".repeat(7)));
    }

    @Test
    void passwordWith21CharactersShouldFailValidation() {
        assertFalse(validatePassword("a".repeat(21)));
    }

    private boolean validatePassword(String password) {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                password,
                password,
                "tester"
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(request).isEmpty();
        }
    }
}


