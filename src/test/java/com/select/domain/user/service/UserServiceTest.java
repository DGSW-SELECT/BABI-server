package com.select.domain.user.service;

import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "users.email"})
    void signUpConvertsKnownEmailConstraintViolationToDuplicateEmail(String constraintName) {
        DataIntegrityViolationException exception = constraintViolation(constraintName);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(exception);

        assertThrows(DuplicateEmailException.class, () -> userService.saveUser(signupRequest()));
    }

    @Test
    void signUpRethrowsOtherDataIntegrityViolation() {
        DataIntegrityViolationException exception = constraintViolation("uq_other_constraint");
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(exception);

        assertSame(exception, assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.saveUser(signupRequest())
        ));
    }

    private SignupRequest signupRequest() {
        return new SignupRequest("signup@example.com", "Test1234!", "Test1234!", "tester");
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate login id",
                new SQLException(),
                "insert into users",
                constraintName
        );
        return new DataIntegrityViolationException("data integrity violation", cause);
    }
}
