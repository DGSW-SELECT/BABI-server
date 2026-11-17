package com.select.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.sql.DriverManager;

public final class IsolatedMySqlProperties {
    private IsolatedMySqlProperties() { }

    public static void register(DynamicPropertyRegistry registry) {
        String port = System.getenv("BABI_ISOLATED_MYSQL_PORT");
        String password = System.getenv("BABI_ISOLATED_MYSQL_PASSWORD");
        String marker = System.getenv("BABI_ISOLATED_MYSQL_MARKER");
        if (port == null || !port.matches("[0-9]{1,5}") || Integer.parseInt(port) <= 0
                || Integer.parseInt(port) > 65535 || "3306".equals(port) || "3307".equals(port)
                || password == null || password.isBlank() || marker == null || !marker.matches("[0-9a-f]{48}")) {
            throw new IllegalStateException("Disposable MySQL test connection is required.");
        }
        String url = "jdbc:mysql://127.0.0.1:" + port + "/babi_invitation_isolated?connectionTimeZone=UTC";
        try (var connection = DriverManager.getConnection(url, "babi_isolated_tester", password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT run_token FROM isolation_guard")) {
            if (!result.next() || !marker.equals(result.getString(1)) || result.next()) {
                throw new IllegalStateException("Disposable MySQL test marker did not match.");
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot validate disposable MySQL test marker.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "babi_isolated_tester");
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");
    }
}
