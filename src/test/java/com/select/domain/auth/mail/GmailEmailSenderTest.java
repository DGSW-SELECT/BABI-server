package com.select.domain.auth.mail;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GmailEmailSenderTest {

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
