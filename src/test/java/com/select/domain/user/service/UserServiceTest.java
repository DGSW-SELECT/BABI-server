package com.select.domain.user.service;

import com.select.domain.auth.service.EmailVerificationService;

import com.select.domain.user.dto.request.LoginRequest;
import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.InvalidCredentialsException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailVerificationService = mock(EmailVerificationService.class);
        userService = new UserService(userRepository, passwordEncoder, mock(JwtProvider.class), emailVerificationService);
    }

    @ParameterizedTest
    @MethodSource("emailConstraintNames")
    void signUpConvertsKnownEmailConstraintViolationToDuplicateEmail(String constraintName) {
        DataIntegrityViolationException exception = constraintViolation(constraintName);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(exception);

        assertThrows(DuplicateEmailException.class, () -> userService.signup(signupRequest()));
    }

    @ParameterizedTest
    @MethodSource("otherConstraintNames")
    void signUpRethrowsOtherDataIntegrityViolation(String constraintName) {
        DataIntegrityViolationException exception = constraintViolation(constraintName);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(exception);

        assertSame(exception, assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.signup(signupRequest())
        ));
    }

    @Test
    void signUpNormalizesUppercaseEmailBeforeSaving() {
        signUpSuccessfully(new SignupRequest(
                "USER@EXAMPLE.COM", "Test1234!", "Test1234!", "tester", "A".repeat(43)
        ));

        assertSavedEmail("user@example.com");
    }

    @Test
    void signUpTrimsEmailBeforeSaving() {
        signUpSuccessfully(new SignupRequest(
                "  user@example.com  ", "Test1234!", "Test1234!", "tester", "A".repeat(43)
        ));

        assertSavedEmail("user@example.com");
    }

    @Test
    void signUpPreventsDuplicateEmailRegardlessOfCase() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.signup(new SignupRequest(
                "USER@EXAMPLE.COM", "Test1234!", "Test1234!", "tester", "A".repeat(43)
        )));

        verify(userRepository).existsByEmail("user@example.com");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void signUpChecksPasswordMismatchBeforeEmailLookup() {
        assertThrows(PasswordMismatchException.class, () -> userService.signup(new SignupRequest(
                "user@example.com", "Test1234!", "Different1234!", "tester", "A".repeat(43)
        )));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Wrong1234!", "babi-dummy-password"})
    void loginWithUnknownEmailRunsRealBCryptAndNeverIssuesToken(String password) {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        BCryptPasswordEncoder realEncoder = spy(new BCryptPasswordEncoder());
        JwtProvider jwtProvider = mock(JwtProvider.class);
        UserService service = new UserService(userRepository, realEncoder, jwtProvider, mock(EmailVerificationService.class));

        assertThrows(InvalidCredentialsException.class, () -> service.login(
                new LoginRequest("  MISSING@EXAMPLE.COM  ", password)
        ));

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(realEncoder).matches(eq(password), hashCaptor.capture());
        // A positive check proves the captured value is an actual hash, not just BCrypt-shaped text.
        assertTrue(BCrypt.checkpw("babi-dummy-password", hashCaptor.getValue()));
        verify(realEncoder, never()).encode(any());
        verify(userRepository).findByEmail("missing@example.com");
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        User user = User.builder().email("user@example.com")
                .passwordHash("valid-hash").nickname("tester").build();
        ReflectionTestUtils.setField(user, "id", 42L);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Test1234!", "valid-hash")).thenReturn(true);
        JwtProvider provider = mock(JwtProvider.class);
        when(provider.createAccessToken("42")).thenReturn("access-token");
        UserService service = new UserService(userRepository, passwordEncoder, provider, mock(EmailVerificationService.class));

        assertEquals("access-token", service.login(
                new LoginRequest("  USER@EXAMPLE.COM  ", "Test1234!")).accessToken());
    }

    @Test
    void signupWithoutVerificationNeverHashesPasswordOrWritesUser() {
        org.mockito.Mockito.doThrow(new com.select.domain.auth.exception.EmailVerificationException(
                com.select.global.exception.ErrorCode.EMAIL_NOT_VERIFIED))
                .when(emailVerificationService).reserveForSignup("signup@example.com", "A".repeat(43));

        assertThrows(com.select.domain.auth.exception.EmailVerificationException.class,
                () -> userService.signup(signupRequest()));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    private void signUpSuccessfully(SignupRequest request) {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.signup(request);
    }

    private void assertSavedEmail(String expectedEmail) {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(expectedEmail, userCaptor.getValue().getEmail());
        verify(userRepository).existsByEmail(expectedEmail);
        verify(emailVerificationService).reserveForSignup(expectedEmail, "A".repeat(43));
    }

    private SignupRequest signupRequest() {
        return new SignupRequest("signup@example.com", "Test1234!", "Test1234!", "tester", "A".repeat(43));
    }

    private static Stream<String> emailConstraintNames() {
        return Stream.of(
                User.EMAIL_UNIQUE_CONSTRAINT,
                "users." + User.EMAIL_UNIQUE_CONSTRAINT,
                User.LEGACY_EMAIL_UNIQUE_CONSTRAINT,
                "users." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT,
                "babi.users." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT,
                "PUBLIC." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT.toUpperCase(Locale.ROOT)
                        + " INDEX PUBLIC." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT.toUpperCase(Locale.ROOT) + "_INDEX_6",
                "PUBLIC." + User.EMAIL_UNIQUE_CONSTRAINT.toUpperCase(Locale.ROOT)
                        + " INDEX PUBLIC." + User.EMAIL_UNIQUE_CONSTRAINT.toUpperCase(Locale.ROOT) + "_INDEX_6"
        );
    }

    private static Stream<String> otherConstraintNames() {
        return Stream.of(
                "uq_other_constraint",
                null,
                "backup_email",
                "email_archive",
                "orders." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT,
                "babi.orders." + User.EMAIL_UNIQUE_CONSTRAINT,
                "public.other_constraint INDEX public." + User.EMAIL_UNIQUE_CONSTRAINT + "_INDEX_6",
                "users." + User.EMAIL_UNIQUE_CONSTRAINT + "_other",
                "public." + User.EMAIL_UNIQUE_CONSTRAINT + "_other INDEX public.some_index"
        );
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
