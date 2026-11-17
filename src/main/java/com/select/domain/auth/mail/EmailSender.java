package com.select.domain.auth.mail;

public interface EmailSender {

    default void validateRecipient(String address) { }

    void send(String to, String subject, String body);
}
