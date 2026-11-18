package com.select.domain.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

@Component
@Profile("discord-demo")
public class DiscordWebhookSettings {
    public static final int MAX_URL_LENGTH = 512;
    private final Path file;
    private volatile URI webhook;

    DiscordWebhookSettings(
            @Value("${DISCORD_WEBHOOK_URL:}") String fallback,
            @Value("${babi.discord.webhook-config-enabled:false}") boolean enabled,
            @Value("${babi.discord.webhook-config-directory:}") String directory
    ) {
        file = enabled ? configurationFile(directory) : null;
        webhook = load(fallback);
    }

    URI current() {
        URI current = webhook;
        if (current == null) {
            throw new IllegalStateException("Discord webhook is not configured");
        }
        return current;
    }

    public synchronized void update(URI endpoint) throws IOException {
        if (file == null) throw new IOException("Discord webhook configuration is disabled");
        URI validated = validate(endpoint.toString());
        Path pending = Files.createTempFile(file.getParent(), "webhook-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(pending, validated.toString(), StandardCharsets.UTF_8);
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            webhook = validated;
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private static Path configurationFile(String directory) {
        try {
            Path path = Path.of(directory);
            if (path.isAbsolute() && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    && Files.isWritable(path)
                    && Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rwx------"))) {
                return path.resolve("webhook-url");
            }
        } catch (IOException | RuntimeException ignored) {
            // Do not propagate environment or file contents into startup logs.
        }
        throw new IllegalStateException("BABI_DISCORD_WEBHOOK_CONFIG_DIRECTORY must be a writable absolute directory with mode 0700");
    }

    private URI load(String fallback) {
        if (file != null && !Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
                                .equals(PosixFilePermissions.fromString("rw-------"))) {
                    throw new IOException();
                }
                try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    return validate(new String(input.readNBytes(MAX_URL_LENGTH + 1), StandardCharsets.UTF_8));
                }
            } catch (IOException | RuntimeException ignored) {
                throw new IllegalStateException("Stored Discord webhook is unreadable or invalid");
            }
        }
        if (fallback.isBlank()) {
            if (file != null) return null;
            throw new IllegalArgumentException("DISCORD_WEBHOOK_URL is required for Discord demo");
        }
        return validate(fallback);
    }

    public static URI validate(String value) {
        if (value != null && value.length() <= MAX_URL_LENGTH && value.matches(
                "(?i:https://discord\\.com)/api/webhooks/[0-9]+/[A-Za-z0-9._-]+")) {
            return URI.create(value);
        }
        throw new IllegalArgumentException("DISCORD_WEBHOOK_URL is not a valid Discord webhook URL");
    }
}
