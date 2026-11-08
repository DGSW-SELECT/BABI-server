package com.select.global.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvitationSqlLogFilterTest {
    @Test
    void hidesInvitationConstraintValuesButKeepsUnrelatedDiagnostics() {
        var context = new LoggerContext();
        var logger = context.getLogger("org.hibernate.orm.jdbc.error");
        var filter = new InvitationSqlLogFilter();
        assertEquals(FilterReply.DENY, filter.decide(null, logger, Level.WARN, "{}",
                new Object[]{"Duplicate entry 'AbC123' for key 'uk_baby_invitation_code'"}, null));
        assertEquals(FilterReply.DENY, filter.decide(null, logger, Level.WARN,
                "Duplicate for uk_baby_invitation_email: private@example.invalid", null, null));
        assertEquals(FilterReply.DENY, filter.decide(null, logger, Level.WARN,
                "Duplicate in allocated_baby_invitation_codes", null, null));
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.WARN, "SQLState: 23505", null, null));
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.WARN, "users schema is unavailable", null, null));
        context.stop();
    }

    @Test
    void springRegistersRealFilterAndRedactsMysqlEventsWithoutSuppressingSqlState() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var existing = new HashSet<TurboFilter>(context.getTurboFilterList());
        Logger jdbc = context.getLogger("org.hibernate.orm.jdbc.error");
        Logger legacyJdbc = context.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
        Logger notice = context.getLogger(InvitationSqlLogFilter.class);
        boolean jdbcAdditive = jdbc.isAdditive(), legacyAdditive = legacyJdbc.isAdditive(), noticeAdditive = notice.isAdditive();
        Level jdbcLevel = jdbc.getLevel(), legacyLevel = legacyJdbc.getLevel(), noticeLevel = notice.getLevel();
        var output = new ListAppender<ILoggingEvent>();
        output.start();
        jdbc.addAppender(output); legacyJdbc.addAppender(output); notice.addAppender(output);
        jdbc.setAdditive(false); legacyJdbc.setAdditive(false); notice.setAdditive(false);
        jdbc.setLevel(Level.WARN); legacyJdbc.setLevel(Level.WARN); notice.setLevel(Level.WARN);
        try (var spring = new AnnotationConfigApplicationContext()) {
            spring.register(InvitationSqlLogFilter.class);
            spring.refresh();
            assertTrue(context.getTurboFilterList().stream().anyMatch(InvitationSqlLogFilter.class::isInstance));
            jdbc.warn("ErrorCode: 1062, SQLState: 23000");
            jdbc.warn("Duplicate entry '{}' for key 'uk_baby_invitation_email'", "private@example.invalid");
            legacyJdbc.warn("Duplicate entry '{}' for key 'db.uk_baby_invitation_code'", "AbC123");
            SQLException nested = new SQLException("Duplicate entry 'secret99' for key 'uk_baby_invitation_code'");
            jdbc.warn("JDBC failure", new IllegalStateException("outer", nested));
            jdbc.warn("unrelated users table error");
            List<String> messages = output.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(messages.stream().anyMatch(s -> s.contains("1062") && s.contains("23000")));
            assertTrue(messages.stream().anyMatch(s -> s.contains("unrelated users")));
            assertEquals(3, messages.stream().filter(s -> s.contains("민감정보 보호")).count());
            assertFalse(messages.toString().contains("private@example.invalid"));
            assertFalse(messages.toString().contains("AbC123"));
            assertFalse(messages.toString().contains("secret99"));
            assertTrue(output.list.stream().noneMatch(e -> e.getThrowableProxy() != null));
        } finally {
            jdbc.detachAppender(output); legacyJdbc.detachAppender(output); notice.detachAppender(output);
            jdbc.setAdditive(jdbcAdditive); legacyJdbc.setAdditive(legacyAdditive); notice.setAdditive(noticeAdditive);
            jdbc.setLevel(jdbcLevel); legacyJdbc.setLevel(legacyLevel); notice.setLevel(noticeLevel);
            for (TurboFilter filter : List.copyOf(context.getTurboFilterList())) {
                if (filter instanceof InvitationSqlLogFilter && !existing.contains(filter)) {
                    context.getTurboFilterList().remove(filter);
                    filter.stop();
                }
            }
            output.stop();
        }
    }
}
