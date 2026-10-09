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

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse saveUser(SignupRequest request) {
        if (userRepository.existsByLoginId(request.email())) {
            throw new DuplicateEmailException();
        }
        if (!request.password().equals(request.passwordConfirm())) {
            throw new PasswordMismatchException();
        }
        User user = User
                .builder()
                .loginId(request.email())
                .nickname(request.nickname())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        try {
            User savedUser = userRepository.saveAndFlush(user);
            return new SignupResponse(savedUser.getId(),
                    savedUser.getLoginId(),
                    savedUser.getNickname());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEmailViolation(e)) {
                throw new DuplicateEmailException();
            }
            throw e;
        }
    }

    private boolean isDuplicateEmailViolation(DataIntegrityViolationException e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();

                if ("uq_users_login_id".equals(name)
                        || "users.uq_users_login_id".equals(name)) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
}
