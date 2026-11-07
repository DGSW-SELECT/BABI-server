package com.select.domain.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SmtpTimeoutConfigTest {
    private ApplicationContextRunner context(String profile) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues("MAIL_USERNAME=sender@example.invalid", "MAIL_PASSWORD=not-a-secret")
                .withInitializer(application -> {
                    application.getEnvironment().setActiveProfiles(profile);
                    ConfigDataEnvironmentPostProcessor.applyTo(application.getEnvironment());
                });
    }

    @Test void bothProfilesConfigureBoundedSmtpOperations() {
        for (String profile : new String[]{"local", "aws"}) {
            context(profile).run(application -> {
                assertNull(application.getStartupFailure());
                var properties = application.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
                assertEquals("5000", properties.getProperty("mail.smtp.connectiontimeout"));
                assertEquals("5000", properties.getProperty("mail.smtp.timeout"));
                assertEquals("5000", properties.getProperty("mail.smtp.writetimeout"));
            });
        }
    }

    @Test void unresponsiveLoopbackSmtpTimesOut() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newSingleThreadExecutor()) {
            var connected = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var peer = executor.submit(() -> {
                try (var socket = server.accept()) {
                    connected.countDown();
                    release.await(12, TimeUnit.SECONDS);
                }
                return null;
            });
            try {
                context("local").withPropertyValues("spring.mail.host=127.0.0.1", "spring.mail.port=" + server.getLocalPort())
                        .run(application -> {
                            assertNull(application.getStartupFailure());
                            var transport = application.getBean(JavaMailSenderImpl.class);
                            var sender = new GmailEmailSender(transport, "sender@example.invalid");
                            long start = System.nanoTime();
                            assertThrows(MailException.class, () -> sender.send("recipient@example.invalid", "test", "test"));
                            long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
                            assertEquals(0, connected.getCount());
                            assertTrue(elapsed >= 4000 && elapsed < 10000, "SMTP read timeout elapsed: " + elapsed);
                        });
            } finally {
                release.countDown();
                peer.get(12, TimeUnit.SECONDS);
            }
        }
    }
}
