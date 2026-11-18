package com.select.domain.auth.service;

import com.select.domain.auth.mail.DiscordWebhookSettings;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;

@Service
@Profile("discord-demo")
@ConditionalOnProperty(name = "babi.discord.webhook-config-enabled", havingValue = "true")
public class DiscordWebhookConfigService {
    public static final int MAX_URL_LENGTH = DiscordWebhookSettings.MAX_URL_LENGTH;
    private final DiscordWebhookSettings settings;

    public DiscordWebhookConfigService(DiscordWebhookSettings settings) {
        this.settings = settings;
    }

    public void update(String url) {
        URI endpoint;
        try {
            endpoint = DiscordWebhookSettings.validate(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        }
        try {
            settings.update(endpoint);
        } catch (IOException | SecurityException e) {
            throw new BusinessException(ErrorCode.DISCORD_WEBHOOK_CONFIG_UNAVAILABLE);
        }
    }
}
