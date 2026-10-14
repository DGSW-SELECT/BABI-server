package com.select.global.security.jwt;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(JwtAuthenticationIntegrationTest.JwtTestConfiguration.class)
class JwtAuthenticationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter;

    @Autowired
    private ServletContext servletContext;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void jwtFilterIsRegisteredInActualServletContainer() {
        assertTrue(jwtFilter.isEnabled());
        assertTrue(servletContext.getFilterRegistrations().values().stream()
                .anyMatch(registration -> registration.getClassName()
                        .equals(JwtAuthenticationFilter.class.getName())
                        && registration.getUrlPatternMappings().contains("/*")));
    }

    @Test
    void validJwtReachesProtectedApiWithUserId() throws Exception {
        var request = request("/test/auth")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken("42"))
                .GET().build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("인증된 사용자 ID: 42", response.body());
    }

    @Test
    void protectedApiWithoutJwtReturns401() throws Exception {
        var response = client.send(request("/test/auth").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    void protectedApiWithInvalidJwtReturns401() throws Exception {
        var response = client.send(request("/test/auth")
                        .header("Authorization", "Bearer not-a-jwt").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth/signup"})
    void publicAuthEndpointsReachValidationWithoutJwt(String path) throws Exception {
        var response = client.send(request(path).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());

        // Invalid input proves MVC was reached, without creating a user or needing credentials.
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("VALIDATION_ERROR"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        JwtTestController jwtTestController() {
            return new JwtTestController();
        }
    }

    @TestComponent
    @RestController
    static class JwtTestController {

        @GetMapping("/test/auth")
        public String testAuth(@RequestAttribute("userId") String userId) {
            return "인증된 사용자 ID: " + userId;
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }
}
