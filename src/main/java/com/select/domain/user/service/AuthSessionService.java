package com.select.domain.user.service;

import com.select.domain.user.dto.request.LoginRequest;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.AccountLifecycleException;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.ErrorCode;
import com.select.global.security.jwt.JwtProvider;
import com.select.global.security.jwt.RefreshSessionService;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {
    private final UserService users;
    private final UserRepository repository;
    private final JwtProvider jwt;
    private final RefreshSessionService sessions;

    public AuthSessionService(UserService users, UserRepository repository, JwtProvider jwt, RefreshSessionService sessions) {
        this.users = users;
        this.repository = repository;
        this.jwt = jwt;
        this.sessions = sessions;
    }

    public Tokens login(LoginRequest request) {
        String userId = users.authenticate(request).toString();
        String refresh = sessions.create(userId);
        return new Tokens(jwt.createAccessToken(userId, RefreshSessionService.sessionId(refresh)), refresh);
    }

    public Tokens refresh(String token) {
        var rotation = sessions.rotate(token);
        Long userId;
        try { userId = Long.valueOf(rotation.userId()); }
        catch (NumberFormatException e) { throw new AccountLifecycleException(ErrorCode.INVALID_TOKEN); }
        if (repository.findById(userId).filter(User::isActive).isEmpty()) {
            sessions.revoke(rotation.token());
            throw new AccountLifecycleException(ErrorCode.INVALID_TOKEN);
        }
        return new Tokens(jwt.createAccessToken(rotation.userId(), RefreshSessionService.sessionId(rotation.token())), rotation.token());
    }

    public record Tokens(String access, String refresh) { }
}
