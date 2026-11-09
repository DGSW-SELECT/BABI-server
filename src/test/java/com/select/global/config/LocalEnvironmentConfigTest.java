package com.select.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalEnvironmentConfigTest {
    @TempDir Path directory;

    @Test
    void localProfileLoadsDotEnvAndExplicitEnvironmentWins() throws Exception {
        Files.writeString(directory.resolve(".env"), """
                EMAIL_VERIFICATION_SECRET=local-test-secret
                DISCORD_WEBHOOK_URL=local-test-placeholder
                MYSQL_PASSWORD=local-db-password
                JWT_SECRET=local-jwt-secret
                """);
        MockEnvironment environment = environment("local", Map.of("DISCORD_WEBHOOK_URL", "override-test-placeholder"));
        assertEquals("local-test-secret", environment.getProperty("email.verification.secret"));
        assertEquals("override-test-placeholder", environment.getProperty("DISCORD_WEBHOOK_URL"));
    }

    @Test
    void missingDotEnvDoesNotSupplySecretAndOtherProfilesDoNotLoadIt() throws Exception {
        MockEnvironment missing = environment("local", Map.of());
        assertThrows(IllegalArgumentException.class, () -> missing.getProperty("email.verification.secret"));
        Files.writeString(directory.resolve(".env"), "EMAIL_VERIFICATION_SECRET=local-test-secret\n");
        MockEnvironment production = environment("prod", Map.of());
        assertNull(production.getProperty("EMAIL_VERIFICATION_SECRET"));
        assertNull(production.getProperty("email.verification.secret"));
    }

    @Test
    void demoResourcesOnlyOverrideLocalWhenDemoProfileIsActive() throws Exception {
        var overrides = Map.<String, Object>of("BABI_DEMO_DB_URL", "jdbc:mysql://127.0.0.1:3308/babi_discord_demo",
                "BABI_DEMO_REDIS_PORT", "16380");
        var normal = environment("local", overrides);
        assertEquals("jdbc:mysql://localhost:3307/babi", normal.getProperty("spring.datasource.url"));
        assertEquals("6379", normal.getProperty("spring.data.redis.port"));
        var demo = environment("local,discord-demo", overrides);
        assertEquals("jdbc:mysql://127.0.0.1:3308/babi_discord_demo", demo.getProperty("spring.datasource.url"));
        assertEquals("16380", demo.getProperty("spring.data.redis.port"));
        assertEquals("", demo.getProperty("spring.mail.username"));
    }

    private MockEnvironment environment(String profile, Map<String, Object> overrides) throws Exception {
        String localConfig = Files.readString(Path.of("src/main/resources/application-local.yml"));
        String absoluteImport = directory.resolve(".env").toUri().toString();
        Path config = directory.resolve("application-local.yml");
        Files.writeString(config, localConfig.replace("file:./.env", absoluteImport));
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("explicitEnvironment", overrides));
        environment.setProperty("spring.config.location", config.toUri().toString());
        environment.setActiveProfiles(profile.split(","));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
}
