package com.select.global.security.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/** Signs an opaque nonce using the HttpOnly refresh credential as a session-bound key. */
final class CsrfToken {
    private CsrfToken() { }

    static String create(String refresh, byte[] nonce) {
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return value + "." + signature(refresh, value);
    }

    static boolean valid(String refresh, String token) {
        if (refresh == null || refresh.isBlank() || token == null || !token.matches("[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}")) return false;
        String[] parts = token.split("\\.");
        return MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII),
                signature(refresh, parts[0]).getBytes(StandardCharsets.US_ASCII));
    }

    private static String signature(String refresh, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(refresh.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(nonce.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("CSRF signing unavailable", e); }
    }
}
