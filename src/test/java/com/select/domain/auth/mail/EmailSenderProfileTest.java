package com.select.domain.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EmailSenderProfileTest {
    private AnnotationConfigApplicationContext context(boolean webhook) {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().setActiveProfiles("local");
        ctx.getEnvironment().getPropertySources().remove("systemEnvironment");
        ctx.getEnvironment().getPropertySources().remove("systemProperties");
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", webhook
                ? Map.of("DISCORD_WEBHOOK_URL", "https://discord.com/api/webhooks/123/test_token") : Map.of()));
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        ctx.register(DiscordWebhookEmailSender.class);
        return ctx;
    }
    @Test void discordIsTheOnlySenderInLocalProfile() {
        try (var ctx = context(true)) {
            ctx.refresh();
            assertEquals(1, ctx.getBeansOfType(EmailSender.class).size());
            assertInstanceOf(DiscordWebhookEmailSender.class, ctx.getBean(EmailSender.class));
        }
    }
    @Test void missingWebhookFailsStartupClearly() {
        try (var ctx = context(false)) {
            Throwable failure = assertThrows(RuntimeException.class, ctx::refresh);
            boolean namedSetting = false;
            while (failure != null) {
                if (String.valueOf(failure.getMessage()).contains("DISCORD_WEBHOOK_URL")) namedSetting = true;
                failure = failure.getCause();
            }
            assertTrue(namedSetting);
        }
    }
}
