package com.select.domain.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordDemoIsolationTest {
    private static final String MARKER = "a".repeat(48);
    private MockEnvironment environment() {
        var env = new MockEnvironment();
        env.setActiveProfiles("local", "discord-demo");
        env.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:13308/babi_discord_demo");
        env.setProperty("spring.sql.init.mode", "never");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        env.setProperty("BABI_DISCORD_DEMO_ENABLED", "true");
        env.setProperty("BABI_DEMO_REDIS_MARKER", MARKER);
        env.setProperty("spring.data.redis.host", "127.0.0.1");
        env.setProperty("spring.data.redis.port", "16380");
        env.setProperty("spring.data.redis.password", "p".repeat(24));
        env.setProperty("spring.data.redis.database", "0");
        return env;
    }
    @SuppressWarnings("unchecked")
    @Test void requiresMatchingRedisMarker() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("babi:discord-demo:isolation-marker")).thenReturn(MARKER, "other");
        assertDoesNotThrow(() -> new DiscordDemoIsolation(environment(), redis, MARKER));
        assertThrows(IllegalStateException.class, () -> new DiscordDemoIsolation(environment(), redis, MARKER));
    }
    @Test void rejectsProductionAndSharedEndpointsBeforeRedisAccess() {
        var redis = mock(StringRedisTemplate.class);
        var env = environment(); env.setActiveProfiles("local", "test");
        assertThrows(IllegalStateException.class, () -> new DiscordDemoIsolation(env, redis, MARKER));
        var shared = environment(); shared.setProperty("spring.data.redis.port", "6379");
        assertThrows(IllegalStateException.class, () -> new DiscordDemoIsolation(shared, redis, MARKER));
        var zero = environment(); zero.setProperty("spring.data.redis.port", "00");
        assertThrows(IllegalStateException.class, () -> new DiscordDemoIsolation(zero, redis, MARKER));
        var mysql = environment(); mysql.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3307/babi");
        assertThrows(IllegalStateException.class, () -> new DiscordDemoIsolation(mysql, redis, MARKER));
        verifyNoInteractions(redis);
    }
    @Test void dedicatedAwsDemoAllowedButProductionProfileRejected() {
        var aws = environment(); aws.setActiveProfiles("aws", "discord-demo");
        assertDoesNotThrow(() -> DiscordDemoIsolation.validateEnvironment(aws));
        aws.setActiveProfiles("aws", "prod");
        assertThrows(IllegalStateException.class, () -> DiscordDemoIsolation.validateEnvironment(aws));
    }
    @Test void mixedProductionProfileFailsBeforeCreatingRedisBean() {
        var redis = mock(StringRedisTemplate.class);
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            var env = environment();
            env.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3307/babi");
            context.setEnvironment(env);
            context.registerBean(StringRedisTemplate.class, () -> redis);
            context.register(DiscordDemoIsolation.class);
            assertThrows(RuntimeException.class, context::refresh);
            verifyNoInteractions(redis);
        }
    }

}
