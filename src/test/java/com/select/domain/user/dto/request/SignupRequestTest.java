
package com.select.domain.user.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupRequestTest {

    @Test
    void passwordWithExactly72Utf8BytesShouldPassValidation() {
        SignupRequest request = requestWithPassword("a".repeat(72));

        assertTrue(request.isPasswordWithin72Bytes());
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void passwordWith73Utf8BytesShouldFailValidation() {
        SignupRequest request = requestWithPassword("a".repeat(73));

        assertFalse(request.isPasswordWithin72Bytes());
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void passwordWithMultibyteCharactersUsesUtf8ByteLength() {
        SignupRequest validLength = new SignupRequest(
                "test@example.com",
                "가".repeat(24),
                "가".repeat(24),
                "tester"
        );

        SignupRequest invalidLength = new SignupRequest(
                "test@example.com",
                "가".repeat(25),
                "가".repeat(25),
                "tester"
        );

        assertTrue(validLength.isPasswordWithin72Bytes());
        assertFalse(invalidLength.isPasswordWithin72Bytes());
    }

    private SignupRequest requestWithPassword(String password) {
        return new SignupRequest("test@example.com", password, password, "test");
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<SignupRequest>> validate(SignupRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(request);
        }
    }
}
