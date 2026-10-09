package com.select.domain.user.service;


import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.domain.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
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
        User savedUser = userRepository.save(user);
        return new SignupResponse(savedUser.getId(),
                savedUser.getLoginId(),
                savedUser.getNickname());
    }
}
