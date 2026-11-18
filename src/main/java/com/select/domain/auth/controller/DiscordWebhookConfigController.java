package com.select.domain.auth.controller;

import com.select.domain.auth.service.DiscordWebhookConfigService;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import com.select.global.response.ApiResponse;
import com.select.global.security.SecurityPaths;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
@Profile("discord-demo")
@ConditionalOnProperty(name = "babi.discord.webhook-config-enabled", havingValue = "true")
public class DiscordWebhookConfigController {
    private final DiscordWebhookConfigService service;
    private final byte[] authorization;

    public DiscordWebhookConfigController(DiscordWebhookConfigService service,
            @Value("${BABI_DISCORD_WEBHOOK_CONFIG_TOKEN:}") String token) {
        if (!token.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalStateException("BABI_DISCORD_WEBHOOK_CONFIG_TOKEN must be a 64-character hex secret");
        }
        this.service = service;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PutMapping(path = SecurityPaths.DISCORD_WEBHOOK_CONFIG, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ApiResponse<Void> update(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !MessageDigest.isEqual(authorization, header.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        byte[] body;
        try {
            body = request.getInputStream().readNBytes(DiscordWebhookConfigService.MAX_URL_LENGTH + 1);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        }
        if (body.length > DiscordWebhookConfigService.MAX_URL_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        }
        service.update(new String(body, StandardCharsets.UTF_8));
        return ApiResponse.success("Discord 웹후크 설정을 저장했습니다.");
    }
}
