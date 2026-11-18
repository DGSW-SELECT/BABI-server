package com.select.domain.auth.mail;

import com.select.domain.auth.controller.DiscordWebhookConfigController;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DiscordWebhookConfigTest {
    private static final String PATH = "/internal/discord/webhook";
    private static final String TOKEN = "a".repeat(64);
    private static final String WEBHOOK = "https://discord.com/api/webhooks/123/test_webhook";
    @TempDir Path directory;

    @Test
    void requiresAdminTokenAndRejectsUnexpectedEndpoints() throws Exception {
        var settings = settings("");
        var mvc = MockMvcBuilders.standaloneSetup(new DiscordWebhookConfigController(settings, TOKEN))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(put(PATH).contentType(MediaType.TEXT_PLAIN).content(WEBHOOK))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).header("Authorization", "Bearer ordinary-user-token")
                        .contentType(MediaType.TEXT_PLAIN).content(WEBHOOK))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.TEXT_PLAIN).content(WEBHOOK + "?unexpected=true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(WEBHOOK))));
        assertTrue(Files.notExists(directory.resolve("webhook-url")));
    }

    @Test
    void registeredAddressAppliesImmediatelyAndSurvivesRestart() throws Exception {
        var settings = settings("");
        assertThrows(IllegalStateException.class, settings::current);
        var mvc = MockMvcBuilders.standaloneSetup(new DiscordWebhookConfigController(settings, TOKEN))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(InputStream.nullInputStream());
        when(response.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);
        var sender = new DiscordWebhookEmailSender(settings::current, new ObjectMapper(), client);

        mvc.perform(put(PATH).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.TEXT_PLAIN).content(WEBHOOK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(WEBHOOK))));
        sender.send("owner@example.invalid", "[BABI] 이메일 인증번호 안내",
                "BABI 이메일 인증번호는 123456입니다.\n인증번호는 5분 동안 유효합니다.");
        verify(client).send(argThat(request -> request.uri().toString().equals(WEBHOOK)), any());
        assertEquals(WEBHOOK, settings("https://discord.com/api/webhooks/456/fallback").current().toString());
        assertEquals(PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(directory.resolve("webhook-url")));
    }

    private DiscordWebhookSettings settings(String fallback) throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return new DiscordWebhookSettings(fallback, true, directory.toString());
    }
}
