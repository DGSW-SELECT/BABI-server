
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
        SignupRequest allowed = requestWithPassword("가".repeat(24));
        SignupRequest rejected = requestWithPassword("가".repeat(25));

        assertTrue(allowed.isPasswordWithin72Bytes());
        assertTrue(validate(allowed).isEmpty());
        assertFalse(rejected.isPasswordWithin72Bytes());
        assertFalse(validate(rejected).isEmpty());
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
