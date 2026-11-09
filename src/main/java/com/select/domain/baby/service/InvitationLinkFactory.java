package com.select.domain.baby.service;

import com.select.domain.baby.exception.BabyInvitationException;
import com.select.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class InvitationLinkFactory {
    private final String baseUrl;

    public InvitationLinkFactory(@Value("${baby.invitation.link-base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String create(String code) {
        try {
            URI uri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !code.matches("[A-Za-z0-9]{6}")) {
                throw new IllegalArgumentException();
            }
            return baseUrl + "?code=" + code;
        } catch (IllegalArgumentException e) {
            throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
        }
    }
}
