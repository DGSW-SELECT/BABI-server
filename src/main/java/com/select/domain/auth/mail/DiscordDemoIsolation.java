package com.select.domain.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.URI;
import java.util.Arrays;

/** Require a dedicated demo database before creating a datasource or enabling Discord delivery. */
@Configuration(proxyBeanMethods = false)
@Profile("!test & !invitation-mysql")
public class DiscordDemoIsolation {
    @Bean
    static BeanFactoryPostProcessor validateDemoEnvironment(Environment env) {
        return beanFactory -> validateEnvironment(env);
    }

    static void validateEnvironment(Environment env) {
        var profiles = Arrays.asList(env.getActiveProfiles());
        String url = env.getProperty("spring.datasource.url", "");
        String port = env.getProperty("spring.data.redis.port", "");
        String password = env.getProperty("spring.data.redis.password", "");
        String marker = env.getProperty("BABI_DEMO_REDIS_MARKER", "");
        boolean demoDb = false;
        try {
            if (url.startsWith("jdbc:mysql://")) {
                URI uri = URI.create(url.substring(5));
                demoDb = uri.getHost() != null && "/babi_discord_demo".equals(uri.getPath()) && uri.getUserInfo() == null;
            }
        } catch (IllegalArgumentException ignored) { }
        if (profiles.size() != 1 || (!profiles.contains("local") && !profiles.contains("aws"))
                || !"true".equals(env.getProperty("BABI_DISCORD_DEMO_ENABLED", "")) || !demoDb
                || !env.getProperty("spring.datasource.jndi-name", "").isEmpty()
                || !"never".equals(env.getProperty("spring.sql.init.mode", "never"))
                || !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto", ""))
                || env.getProperty("spring.data.redis.host", "").isBlank()
                || !port.matches("[0-9]{2,5}") || Integer.parseInt(port) == 6379 || Integer.parseInt(port) > 65535
                || password.length() < 24 || !"0".equals(env.getProperty("spring.data.redis.database", "0"))
                || !env.getProperty("spring.data.redis.url", "").isEmpty()
                || !env.getProperty("spring.data.redis.sentinel.master", "").isEmpty()
                || !env.getProperty("spring.data.redis.cluster.nodes", "").isEmpty()
                || !marker.matches("[a-f0-9]{48,}")) {
            throw new IllegalStateException("Discord requires explicit demo mode, a dedicated babi_discord_demo MySQL database and authenticated dedicated Redis");
        }
    }

    public DiscordDemoIsolation(Environment env, StringRedisTemplate redis,
            @Value("${BABI_DEMO_REDIS_MARKER:}") String marker) {
        validateEnvironment(env);
        try {
            if (!marker.equals(redis.opsForValue().get("babi:discord-demo:isolation-marker"))) throw new IllegalStateException();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Discord demo Redis isolation verification failed");
        }
    }
}
