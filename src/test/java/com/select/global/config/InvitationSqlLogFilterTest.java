package com.select.global.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
