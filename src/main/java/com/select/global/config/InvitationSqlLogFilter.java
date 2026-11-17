package com.select.global.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.sql.SQLException;

/** Hibernate logs unique-index values before an application can catch the exception. */
@Component
public class InvitationSqlLogFilter extends TurboFilter {
    @PostConstruct
    public void register() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            synchronized (context) {
                if (context.getTurboFilterList().stream().noneMatch(InvitationSqlLogFilter.class::isInstance)) {
                    start();
                    context.addTurboFilter(this);
                }
            }
        }
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        if (!logger.getName().equals("org.hibernate.orm.jdbc.error")
                && !logger.getName().equals("org.hibernate.engine.jdbc.spi.SqlExceptionHelper")) return FilterReply.NEUTRAL;
        StringBuilder message = new StringBuilder(String.valueOf(format));
        if (params != null) for (Object value : params) message.append(' ').append(value);
        if (throwable != null) appendExceptionMessages(message, throwable);
        String text = message.toString().toLowerCase(Locale.ROOT);
        if (text.contains("baby_invitation_code") || text.contains("baby_email_invitation")
                || text.contains("uk_baby_invitation_email")) {
            // Use a different logger and no original message/throwable: retain an observable event
            // while SQLState/error-code events logged separately by Hibernate remain visible.
            logger.getLoggerContext().getLogger(InvitationSqlLogFilter.class)
                    .warn("초대 관련 JDBC 오류 상세를 민감정보 보호를 위해 생략했습니다.");
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }

    private void appendExceptionMessages(StringBuilder message, Throwable root) {
        var pending = new ArrayDeque<Throwable>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        while (!pending.isEmpty() && seen.size() < 64) {
            Throwable current = pending.removeFirst();
            if (!seen.add(current)) continue;
            message.append(' ').append(current.getMessage());
            if (current.getCause() != null) pending.add(current.getCause());
            for (Throwable suppressed : current.getSuppressed()) pending.add(suppressed);
            if (current instanceof SQLException sql && sql.getNextException() != null) pending.add(sql.getNextException());
        }
    }
}
