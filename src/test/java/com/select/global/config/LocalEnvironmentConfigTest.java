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
                MAIL_USERNAME=local@example.invalid
                MAIL_PASSWORD=local-test-password
                MYSQL_PASSWORD=local-db-password
                JWT_SECRET=local-jwt-secret
                """);
        MockEnvironment environment = environment("local", Map.of("MAIL_USERNAME", "override@example.invalid"));
        assertEquals("local-test-secret", environment.getProperty("email.verification.secret"));
        assertEquals("override@example.invalid", environment.getProperty("spring.mail.username"));
        assertEquals("local-test-password", environment.getProperty("spring.mail.password"));
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

    private MockEnvironment environment(String profile, Map<String, Object> overrides) throws Exception {
        String localConfig = Files.readString(Path.of("src/main/resources/application-local.yml"));
        String absoluteImport = directory.resolve(".env").toUri().toString();
        Path config = directory.resolve("application-local.yml");
        Files.writeString(config, localConfig.replace("file:./.env", absoluteImport));
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("explicitEnvironment", overrides));
        environment.setProperty("spring.config.location", config.toUri().toString());
        environment.setActiveProfiles(profile);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
}
