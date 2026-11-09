package com.select.domain.auth.mail;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/** Demo-only transport: never forward addresses, links or arbitrary email content. */
@Component
@Profile("discord-demo")
public class DiscordWebhookEmailSender implements EmailSender {
    private static final Pattern TEST_ADDRESS = Pattern.compile("[a-zA-Z0-9._%+\\-]+@example\\.invalid", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERIFY = Pattern.compile("\\ABABI 이메일 인증번호는 ([0-9]{6})입니다\\.\\n인증번호는 5분 동안 유효합니다\\.\\z");
    private static final Pattern INVITE = Pattern.compile("\\ABABI 아기 보호자로 초대되었습니다\\.\\n초대 코드: ([A-Z0-9]{6})\\n초대 링크: [^\\n]+\\n이메일 초대 유효기간: ([0-9TZ:.-]+)\\n로그인한 후 초대 코드를 입력해 주세요\\.\\z");
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscordWebhookEmailSender.class);
    private final URI webhook;
    private final HttpClient client;
    private final ObjectMapper json;

    @Autowired
    public DiscordWebhookEmailSender(@Value("${DISCORD_WEBHOOK_URL:}") String url, ObjectMapper json) {
        this(url, json, createClient());
    }

    static HttpClient createClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    DiscordWebhookEmailSender(String url, ObjectMapper json, HttpClient client) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("DISCORD_WEBHOOK_URL is required for Discord demo");
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"discord.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9._-]+")) throw new IllegalArgumentException();
            this.webhook = uri;
        } catch (RuntimeException ex) { throw new IllegalArgumentException("DISCORD_WEBHOOK_URL is not a valid Discord webhook URL"); }
        this.client = client;
        this.json = json;
    }

    // Package-private test seam: production configuration always validates a Discord HTTPS URL.
    DiscordWebhookEmailSender(URI loopback, ObjectMapper json, HttpClient client) {
        if (!"http".equals(loopback.getScheme()) || !"127.0.0.1".equals(loopback.getHost()))
            throw new IllegalArgumentException("Test endpoint must be loopback");
        this.webhook = loopback;
        this.client = client;
        this.json = json;
    }

    @Override
    public void validateRecipient(String to) {
        if (to == null || !TEST_ADDRESS.matcher(to).matches())
            throw new com.select.domain.auth.exception.EmailVerificationException(com.select.global.exception.ErrorCode.EMAIL_DEMO_ADDRESS_REQUIRED);
    }

    @Override
    public void send(String to, String subject, String body) {
        validateRecipient(to);
        String message;
        var verify = VERIFY.matcher(body == null ? "" : body);
        var invite = INVITE.matcher(body == null ? "" : body);
        if ("[BABI] 이메일 인증번호 안내".equals(subject) && verify.matches()) {
            message = "[BABI 회원가입 인증]\n인증번호: " + verify.group(1) + "\n유효기간: 5분\n시연 전용";
        } else if ("[BABI] 아기 보호자 초대".equals(subject) && invite.matches()) {
            message = "[BABI 보호자 초대]\n초대 코드: " + invite.group(1) + "\n이메일 초대 만료 시각: " + invite.group(2) + "\n시연 전용";
        } else throw new IllegalArgumentException("Unsupported demo message");
        int status;
        try {
            String payload = json.writeValueAsString(Map.of("content", message, "allowed_mentions", Map.of("parse", java.util.List.of())));
            HttpRequest request = HttpRequest.newBuilder(webhook).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            // Discord's response body is not needed. Close it immediately after the headers
            // so a stalled response body cannot keep the invitation transaction open.
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var bodyStream = response.body()) {
                status = response.statusCode();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Discord delivery interrupted");
            throw new IllegalStateException("Discord delivery interrupted");
        } catch (Exception ex) {
            // Neither exception messages, URLs, nor request/response contents are logged or propagated.
            log.warn("Discord delivery failed (transport error)");
            throw new IllegalStateException("Discord delivery failed");
        }
        if (status < 200 || status >= 300) {
            log.warn("Discord delivery failed (HTTP status {})", status);
            throw new IllegalStateException(status == 429 ? "Discord rate limited" : "Discord delivery failed");
        }
    }
}
