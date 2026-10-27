package com.select.global.security;

public class SecurityPaths {
    public static final String LOGIN = "/auth/login";
    public static final String SIGNUP = "/auth/signup";
    public static final String EMAIL_SEND = "/auth/email/send";
    public static final String EMAIL_VERIFY = "/auth/email/verify";

    public static boolean isDocumentationPath(String path) {
        return "/swagger-ui.html".equals(path)
                || path.startsWith("/swagger-ui/")
                || "/v3/api-docs".equals(path)
                || path.startsWith("/v3/api-docs/")
                || "/v3/api-docs.yaml".equals(path);
    }

    private SecurityPaths() {}
}
