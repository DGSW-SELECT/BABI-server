package com.select.global.config;

import com.select.domain.auth.mail.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailSender emailSender;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void openApiDescribesActualApisAndJwtWithoutRequiringAuthentication() throws Exception {
        var response = get("/v3/api-docs");
        assertEquals(200, response.statusCode(), response.body());
        var document = objectMapper.readTree(response.body());
        assertEquals("BABI API", document.path("info").path("title").asString());
        var scheme = document.path("components").path("securitySchemes").path("bearerAuth");
        assertEquals("http", scheme.path("type").asString());
        assertEquals("bearer", scheme.path("scheme").asString());
        assertEquals("JWT", scheme.path("bearerFormat").asString());
        assertTrue(document.path("security").get(0).has("bearerAuth"));
        for (String path : List.of("/auth/login", "/auth/signup", "/auth/email/send", "/auth/email/verify")) {
            var operation = document.path("paths").path(path).path("post");
            assertFalse(operation.isMissingNode(), path);
            assertTrue(operation.path("security").isArray(), path);
            assertTrue(operation.path("security").isEmpty(), path);
        }
        assertTrue(document.path("components").path("schemas").path("SignupRequest")
                .path("properties").has("verificationToken"));
        assertFalse(document.path("paths").has("/test/auth"));
        verifyNoInteractions(emailSender);
    }

    @Test
    void swaggerUiAssetsConfigurationAndYamlAreAccessibleWithoutJwt() throws Exception {
        var redirect = get("/swagger-ui.html");
        assertEquals(302, redirect.statusCode());
        assertTrue(redirect.headers().firstValue("Location").orElseThrow().endsWith("/swagger-ui/index.html"));
        var page = get("/swagger-ui/index.html");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Swagger UI"));
        assertEquals(200, get("/swagger-ui/swagger-initializer.js").statusCode());
        assertEquals(200, get("/swagger-ui/swagger-ui-bundle.js").statusCode());
        var configuration = get("/v3/api-docs/swagger-config");
        assertEquals(200, configuration.statusCode());
        assertEquals("/v3/api-docs", objectMapper.readTree(configuration.body()).path("url").asString());
        var yaml = get("/v3/api-docs.yaml");
        assertEquals(200, yaml.statusCode());
        assertTrue(yaml.body().contains("openapi:"));
        verifyNoInteractions(emailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs-extra", "/swagger-ui.html/extra", "/private-resource"})
    void pathsOutsideDocumentationStillRequireJwt(String path) throws Exception {
        var response = get(path);
        assertEquals(401, response.statusCode());
        assertEquals("UNAUTHORIZED", objectMapper.readTree(response.body()).path("error").path("code").asString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
