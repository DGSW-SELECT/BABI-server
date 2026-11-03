package com.select.domain.auth.mail;

public interface EmailSender {

    void send(String to, String subject, String body);
}
