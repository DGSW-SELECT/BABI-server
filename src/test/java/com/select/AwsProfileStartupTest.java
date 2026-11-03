package com.select;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test,aws",
        "spring.datasource.url=jdbc:h2:mem:babi-aws-profile;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "email.verification.secret=test-only-independent-email-secret",
        "jwt.secret=${random.uuid}${random.uuid}"
})
class AwsProfileStartupTest {

    @LocalServerPort private int port;
    @Autowired private Environment environment;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void advertisesHttpsInOpenApiBehindLoopbackProxy() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443")
                .GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        var server = URI.create(objectMapper.readTree(response.body())
                .path("servers").path(0).path("url").asText());
        assertEquals("https", server.getScheme());
        assertEquals(-1, server.getPort());
    }

    @Test
    void initializesEmptyDatabaseBeforeValidationAndStartsAuthenticatedApi() throws Exception {
        assertTrue(Arrays.asList(environment.getActiveProfiles()).contains("aws"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feeding_records", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_reports", Long.class));
        assertEquals("127.0.0.1", environment.getProperty("server.address"));

        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/babi/api/babies/1/daily-reports/2026-10-27")).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(401, response.statusCode());
    }
}
