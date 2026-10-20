package com.select.domain.user.service;

import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        validateSignup(request, email);
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
    //회원가입이 올바른지 설명
    private void validateSignup(SignupRequest request, String email) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new PasswordMismatchException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
    }

    //정규화한 이메일 반환
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    //데이터베이스에서 나는 오류가 이메일 때문인지 확인
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

    //제약조건 오류가 이메일 중복때문에 발생했는지 확인
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
}
