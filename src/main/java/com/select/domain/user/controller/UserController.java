package com.select.domain.user.controller;

import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.service.UserService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/babi/api/v1/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> saveUser(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.saveUser(request);
        return new ApiResponse<SignupResponse>(
                true,
                "회원가입이 완료되었습니다.",
                response
        );
    }
}
