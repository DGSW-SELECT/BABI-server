package com.select.domain.user.service;

import com.select.domain.auth.service.EmailVerificationService;
import com.select.global.util.EmailNormalizer;
import com.select.domain.user.dto.request.LoginRequest;
import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.dto.response.LoginResponse;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.InvalidCredentialsException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailVerificationService emailVerificationService;
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$G48i72gfQyJ7URWzsalGbeZsaw79R8FDiHbAQBaAoYOLgHqoRBQiG";

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        validateSignup(request, email);
        emailVerificationService.reserveForSignup(email, request.verificationToken());
        User user = User
                .builder()
                .email(email)
                .nickname(request.nickname())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        try {
            User savedUser = userRepository.saveAndFlush(user);
            return new SignupResponse(savedUser.getId(),
                    savedUser.getEmail(),
                    savedUser.getNickname());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEmailViolation(e)) {
                throw new DuplicateEmailException();
            }
            throw e;
        }
    }

    private void validateSignup(SignupRequest request, String email) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new PasswordMismatchException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
    }


    private String normalizeEmail(String email) {
        return EmailNormalizer.normalize(email);
    }

    private boolean isDuplicateEmailViolation(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (matchesEmailConstraint(name)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean matchesEmailConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }

        String name = constraintName.toLowerCase(Locale.ROOT);
        int indexDescription = name.indexOf(" index ");
        if (indexDescription >= 0) {
            name = name.substring(0, indexDescription);
        }

        int qualifier = name.lastIndexOf('.');
        if (qualifier >= 0 && indexDescription < 0) {
            String namespace = name.substring(0, qualifier);
            if (!"users".equals(namespace)
                    && !namespace.endsWith(".users")
                    && !"public".equals(namespace)) {
                return false;
            }
        }

        String localName = name.substring(qualifier + 1);
        return User.EMAIL_UNIQUE_CONSTRAINT.equals(localName)
                || User.LEGACY_EMAIL_UNIQUE_CONSTRAINT.equals(localName);
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new InvalidCredentialsException();
        }
        User user = optionalUser.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String accessToken = jwtProvider.createAccessToken(user.getId().toString());
        return new LoginResponse(accessToken);
    }

}
