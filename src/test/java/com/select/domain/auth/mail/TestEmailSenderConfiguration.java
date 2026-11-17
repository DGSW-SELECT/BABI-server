package com.select.domain.auth.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestEmailSenderConfiguration {
    @Bean
    EmailSender testEmailSender() {
        return (to, subject, body) -> { throw new IllegalStateException("No email transport in tests"); };
    }
}
