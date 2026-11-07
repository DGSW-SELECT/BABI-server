package com.select.global.security.jwt;

import com.select.domain.auth.service.EmailVerificationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.select.domain.user.controller.UserController;
import com.select.global.exception.ErrorCode;
import com.select.global.security.SecurityPaths;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(JwtAuthenticationIntegrationTest.JwtTestConfiguration.class)
class JwtAuthenticationIntegrationTest {

    // 이메일 인증 자체는 별도 실제 Redis 통합 테스트에서 검증한다.
    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter;

    @Autowired
    private ServletContext servletContext;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void securityPathsMatchActualControllerPostMappings() {
        assertControllerPostMapping("login", SecurityPaths.LOGIN);
        assertControllerPostMapping("signup", SecurityPaths.SIGNUP);
    }

    private void assertControllerPostMapping(String methodName, String expectedPath) {
        var mappings = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().equals(UserController.class)
                        && entry.getValue().getMethod().getName().equals(methodName))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals(1, mappings.size(), methodName + " 매핑은 하나여야 합니다.");
        assertEquals(Set.of(expectedPath), mappings.getFirst().getPatternValues());
        assertEquals(Set.of(RequestMethod.POST), mappings.getFirst().getMethodsCondition().getMethods());
    }

    @Test
    void jwtFilterIsRegisteredOnlyOnceInSecurityChain() {
        assertFalse(jwtFilter.isEnabled());
        assertSame(jwtAuthenticationFilter, jwtFilter.getFilter());
        assertEquals(1L, securityFilterChain.getFilters().stream()
                .filter(JwtAuthenticationFilter.class::isInstance).count());
        assertTrue(securityFilterChain.getFilters().contains(jwtAuthenticationFilter));
        assertTrue(servletContext.getFilterRegistrations().values().stream()
                .noneMatch(registration -> registration.getClassName()
                        .equals(JwtAuthenticationFilter.class.getName())));
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

        assertErrorResponse(response, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void protectedApiWithInvalidJwtReturns401() throws Exception {
        var response = client.send(request("/test/auth")
                        .header("Authorization", "Bearer not-a-jwt").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.INVALID_TOKEN);
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

    @Test
    void validRequestParsesJwtOnlyOnce() throws Exception {
        String token = jwtProvider.createAccessToken("43");
        var response = client.send(request("/test/auth")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("인증된 사용자 ID: 43", response.body());
        verify(jwtProvider, times(1)).getUserId(token);
        verify(jwtAuthenticationFilter, times(1)).doFilter(any(), any(), any());
    }

    @Test
    void anonymousRequestReachingSecurityEntryPointReturns401() throws Exception {
        // Non-CORS OPTIONS bypasses the JWT filter, but still requires authentication in Security.
        var response = client.send(request("/test/auth")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void authenticatedDownstreamAccessDeniedReturns403() throws Exception {
        var response = client.send(request("/test/filter-denied")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.FORBIDDEN);
        assertTrue(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).isEmpty());
    }

    @Test
    void authenticatedControllerAccessDeniedReturns403() throws Exception {
        var response = client.send(request("/test/controller-denied")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.FORBIDDEN);
    }

    @Test
    void signupThenLoginTokenGrantsAccessToProtectedApi() throws Exception {
        String email = "jwt-" + UUID.randomUUID() + "@example.com";
        String signupBody = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "Test1234!",
                "passwordConfirm", "Test1234!", "nickname", "jwt-tester", "verificationToken", "A".repeat(43)
        ));
        var signupResponse = client.send(request("/auth/signup")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(signupBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, signupResponse.statusCode());
        var signupJson = objectMapper.readTree(signupResponse.body());
        assertTrue(signupJson.get("success").asBoolean());
        assertEquals(email, signupJson.get("data").get("email").asString());
        assertNotNull(signupJson.get("data").get("userId"));
        String userId = Long.toString(signupJson.get("data").get("userId").asLong());

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "Test1234!"
        ));
        var loginResponse = client.send(request("/auth/login")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResponse.statusCode());
        var loginJson = objectMapper.readTree(loginResponse.body());
        assertTrue(loginJson.get("success").asBoolean());
        String token = loginJson.get("data").get("accessToken").asString();
        assertFalse(token.isBlank());

        var protectedResponse = client.send(request("/test/auth")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, protectedResponse.statusCode());
        assertEquals("인증된 사용자 ID: " + userId, protectedResponse.body());

        var anonymousResponse = client.send(request("/test/auth").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertErrorResponse(anonymousResponse, ErrorCode.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownEmailAndWrongPasswordKeepSame400Response(boolean registered) throws Exception {
        String email = "login-failure-" + UUID.randomUUID() + "@example.com";
        if (registered) {
            String signupBody = objectMapper.writeValueAsString(Map.of(
                    "email", email, "password", "Correct1234!",
                    "passwordConfirm", "Correct1234!", "nickname", "login-tester", "verificationToken", "A".repeat(43)
            ));
            var signupResponse = client.send(request("/auth/signup")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .POST(HttpRequest.BodyPublishers.ofString(signupBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, signupResponse.statusCode());
        }

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "Wrong1234!"
        ));
        var response = client.send(request("/auth/login")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.INVALID_CREDENTIALS);
        assertEquals(400, response.statusCode());
    }

    private void assertErrorResponse(HttpResponse<String> response, ErrorCode errorCode) throws Exception {
        assertEquals(errorCode.getHttpStatus().value(), response.statusCode(), response.body());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(
                response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()
        )));
        var body = objectMapper.readTree(response.body());
        assertFalse(body.get("success").asBoolean());
        assertEquals(errorCode.name(), body.get("error").get("code").asString());
        assertEquals(errorCode.getMessage(), body.get("message").asString());
        assertTrue(body.get("data").isNull());
        assertTrue(body.get("error").get("details").isArray());
        assertTrue(body.get("error").get("details").isEmpty());
        if (errorCode.getHttpStatus().value() == 401) {
            assertEquals("Bearer", response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElseThrow());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        FilterRegistrationBean<Filter> testAccessDeniedFilter() {
            // Simulate a downstream authorization denial without replacing the production SecurityFilterChain.
            Filter filter = (request, response, chain) -> {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                assertNotNull(authentication);
                assertTrue(authentication.isAuthenticated());
                throw new AccessDeniedException("test-only denied resource");
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setUrlPatterns(List.of("/test/filter-denied"));
            registration.setOrder(Ordered.LOWEST_PRECEDENCE);
            return registration;
        }

        @Bean
        JwtTestController jwtTestController() {
            return new JwtTestController();
        }
    }

    @TestComponent
    @RestController
    static class JwtTestController {

        @GetMapping("/test/controller-denied")
        public void controllerDenied() {
            throw new AccessDeniedException("test-only denied controller");
        }

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
