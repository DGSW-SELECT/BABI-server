package com.select.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class InvitationMysqlTestConfigTest {
    @Test
    void retiredProfileFailsBeforeCreatingDatabaseRedisOrEmailBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("invitation-mysql");
            context.register(InvitationMysqlTestConfig.class);
            var failure = assertThrows(IllegalStateException.class, context::refresh);
            assertTrue(failure.getMessage().contains("legacy invitation-mysql profile is disabled"));
        }
    }
}
