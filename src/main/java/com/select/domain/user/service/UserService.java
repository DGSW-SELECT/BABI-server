package com.select.domain.user.service;


import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.entity.User;
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
    public void saveUser(SignupRequest request) {
        if (userRepository.existsByLoginId(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        User user = User
                .builder()
                .loginId(request.getEmail())
                .nickname(request.getNickname())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);
    }
}
