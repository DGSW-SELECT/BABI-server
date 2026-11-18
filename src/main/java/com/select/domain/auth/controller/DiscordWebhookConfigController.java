package com.select.domain.auth.controller;

import com.select.domain.auth.mail.DiscordWebhookSettings;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
@Profile("discord-demo")
@ConditionalOnProperty(name = "babi.discord.webhook-config-enabled", havingValue = "true")
public class DiscordWebhookConfigController {
    private final DiscordWebhookSettings settings;
    private final byte[] authorization;

    public DiscordWebhookConfigController(DiscordWebhookSettings settings,
            @Value("${BABI_DISCORD_WEBHOOK_CONFIG_TOKEN:}") String token) {
        if (!token.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalStateException("BABI_DISCORD_WEBHOOK_CONFIG_TOKEN must be a 64-character hex secret");
        }
        this.settings = settings;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PutMapping(path = SecurityPaths.DISCORD_WEBHOOK_CONFIG, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ApiResponse<Void> update(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !MessageDigest.isEqual(authorization, header.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        URI endpoint;
        try {
            byte[] body = request.getInputStream().readNBytes(DiscordWebhookSettings.MAX_URL_LENGTH + 1);
            if (body.length > DiscordWebhookSettings.MAX_URL_LENGTH) {
                throw new IllegalArgumentException();
            }
            endpoint = DiscordWebhookSettings.validate(new String(body, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        }
        try {
            settings.update(endpoint);
        } catch (IOException | SecurityException e) {
            throw new BusinessException(ErrorCode.DISCORD_WEBHOOK_CONFIG_UNAVAILABLE);
        }
        return new ApiResponse<>(true, "Discord 웹후크 설정을 저장했습니다.", null);
    }
}
