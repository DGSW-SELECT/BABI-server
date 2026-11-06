package com.select.domain.auth.mail;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class GmailEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(GmailEmailSender.class);
    private final JavaMailSender mailSender;
    private final String from;

    public GmailEmailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (from.isBlank()) {
            throw new EmailVerificationException(ErrorCode.EMAIL_SEND_FAILED);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(from);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // SMTP exception messages can contain recipients, message contents, or credentials.
            String causeType = e.getCause() == null ? "none" : e.getCause().getClass().getName();
            log.error("SMTP 발송에 실패했습니다. exceptionType={} causeType={}",
                    e.getClass().getName(), causeType);
            throw e;
        }
    }
}
