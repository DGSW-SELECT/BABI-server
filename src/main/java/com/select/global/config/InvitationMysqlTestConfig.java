package com.select.global.config;

import com.select.domain.auth.mail.EmailSender;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

/** Isolation adapters for manual MySQL/Redis tests; inactive in local and H2 profiles. */
@Configuration(proxyBeanMethods = false)
@Profile("invitation-mysql")
public class InvitationMysqlTestConfig {
    private static final String PREFIX = "babi_invitation_test:";

    @Bean
    static BeanFactoryPostProcessor requireIsolatedConnections() {
        return beanFactory -> {
            throw new IllegalStateException("The legacy invitation-mysql profile is disabled; use scripts/run_isolated_invitation_mysql.py.");
        };
    }

    @Bean(name = "stringRedisTemplate")
    StringRedisTemplate isolatedRedisTemplate(RedisConnectionFactory connectionFactory) {
        var template = new StringRedisTemplate(connectionFactory);
        var strings = RedisSerializer.string();
        template.setKeySerializer(new RedisSerializer<String>() {
            @Override
            public byte[] serialize(String value) {
                return value == null ? null : strings.serialize(PREFIX + value);
            }

            @Override
            public String deserialize(byte[] bytes) {
                String value = strings.deserialize(bytes);
                if (value == null) return null;
                if (!value.startsWith(PREFIX)) throw new SerializationException("Unexpected Redis test namespace.");
                return value.substring(PREFIX.length());
            }
        });
        return template;
    }

    @Bean
    @Primary
    EmailSender localInvitationTestMailbox() {
        return (to, subject, body) -> {
            try {
                Path directory = Path.of("work", "invitation-mysql-mail");
                Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                Path file = Files.createFile(directory.resolve(UUID.randomUUID() + ".txt"),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.writeString(file, "To: " + to + "\nSubject: " + subject + "\n\n" + body);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot save isolated test mail.");
            }
        };
    }
}
