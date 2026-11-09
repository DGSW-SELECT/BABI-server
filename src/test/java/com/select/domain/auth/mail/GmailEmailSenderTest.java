package com.select.domain.auth.mail;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class GmailEmailSenderTest {
    @Test
    void mailFailureLogsOnlyExceptionTypesAndRethrows(CapturedOutput output) {
        JavaMailSender transport = mock(JavaMailSender.class);
        MailSendException failure = new MailSendException("private-password-code", new IllegalStateException("private-smtp-detail"));
        doThrow(failure).when(transport).send(any(SimpleMailMessage.class));
        GmailEmailSender sender = new GmailEmailSender(transport, "sender@example.invalid");

        assertSame(failure, assertThrows(MailSendException.class,
                () -> sender.send("recipient@example.invalid", "private-subject", "private-message")));
        assertTrue(output.getAll().contains(MailSendException.class.getName()));
        assertTrue(output.getAll().contains(IllegalStateException.class.getName()));
        assertFalse(output.getAll().contains("private-"));
        assertFalse(output.getAll().contains("@example.invalid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void missingSmtpAccountFailsWithoutSendingMail(String from) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        var sender = new GmailEmailSender(mailSender, from);
        var error = assertThrows(EmailVerificationException.class,
                () -> sender.send("demo@example.invalid", "subject", "body"));
        assertEquals(ErrorCode.EMAIL_SEND_FAILED, error.getErrorCode());
        verifyNoInteractions(mailSender);
    }
}
