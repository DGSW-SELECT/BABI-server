package com.select.global.exception;

import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import com.select.global.response.ErrorDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handleExceptionReturns500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleUnexpectedException(
                new RuntimeException("테스트 오류")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        var body = response.getBody();

        assertNotNull(body);
        assertNotNull(body.error());
        assertEquals("INTERNAL_SERVER_ERROR", body.error().code());
        assertTrue(body.error().details().isEmpty());
        assertFalse(body.success());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), body.message());
        assertNull(body.data());
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpectAll(apiError(ErrorCode.NOT_FOUND));
    }

    @Test
    void missingRequiredParameterReturns400() throws Exception {
        mockMvc.perform(get("/exception-test/required"))
                .andExpect(status().isBadRequest())
                .andExpectAll(apiError(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/exception-test/content-type")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpectAll(apiError(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/exception-test/content-type"))
                .andExpect(status().isMethodNotAllowed())
                .andExpectAll(apiError(ErrorCode.METHOD_NOT_ALLOWED))
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"));
    }

    @Test
    void validationFailureReturns400() throws Exception {
        mockMvc.perform(post("/exception-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpectAll(apiError(ErrorCode.VALIDATION_ERROR))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("value"))
                .andExpect(jsonPath("$.error.details[0].reason").value("value는 필수입니다."));
    }

    @Test
    void unexpectedServerErrorReturns500() throws Exception {
        mockMvc.perform(get("/exception-test/error"))
                .andExpect(status().isInternalServerError())
                .andExpectAll(apiError(ErrorCode.INTERNAL_SERVER_ERROR))
                .andExpect(content().string(not(containsString("test error"))))
                .andExpect(content().string(not(containsString("test checked error"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void unexpectedCheckedExceptionReturns500() throws Exception {
        mockMvc.perform(get("/exception-test/checked-error"))
                .andExpect(status().isInternalServerError())
                .andExpectAll(apiError(ErrorCode.INTERNAL_SERVER_ERROR))
                .andExpect(content().string(not(containsString("test error"))))
                .andExpect(content().string(not(containsString("test checked error"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @ParameterizedTest
    @CsvSource({
            "400, VALIDATION_ERROR", "401, UNAUTHORIZED", "403, FORBIDDEN",
            "404, NOT_FOUND", "405, METHOD_NOT_ALLOWED", "415, UNSUPPORTED_MEDIA_TYPE",
            "500, INTERNAL_SERVER_ERROR", "503, INTERNAL_SERVER_ERROR",
            "406, REQUEST_ERROR", "499, REQUEST_ERROR", "599, INTERNAL_SERVER_ERROR"
    })
    void mvcStatusExceptionPreservesStatusAndHeaders(int statusCode, ErrorCode errorCode) throws Exception {
        mockMvc.perform(get("/exception-test/status").param("status", String.valueOf(statusCode)))
                .andExpect(status().is(statusCode))
                .andExpectAll(apiError(errorCode))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(content().string(not(containsString("internal diagnostic"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void committedResponseIsNotRewritten() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        response.flushBuffer();

        assertNull(new GlobalExceptionHandler().handleException(
                new HttpMessageNotWritableException("internal diagnostic"),
                new ServletWebRequest(request, response)
        ));
    }

    @Test
    void mvcServerErrorRetainsSpringExceptionAttribute() throws Exception {
        var request = new MockHttpServletRequest();
        var exception = new HttpMessageNotWritableException("internal diagnostic");

        var response = new TestableGlobalExceptionHandler().handleInternalServerError(
                exception, new ServletWebRequest(request, new MockHttpServletResponse())
        );

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertSame(exception, request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE));
    }

    @Test
    void existingApiResponseRetainsDetails() {
        var body = new ApiResponse<Void>(false, ErrorCode.VALIDATION_ERROR.getMessage(), null,
                new ApiError("VALIDATION_ERROR", List.of(new ErrorDetail("value", "required"))));
        var response = new GlobalExceptionHandler().createResponseEntity(
                body, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse())
        );

        assertNotNull(response);
        assertSame(body, response.getBody());
    }

    @Test
    void malformedJsonRetainsSpecificErrorCode() throws Exception {
        mockMvc.perform(post("/exception-test/validation")
                        .contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpectAll(apiError(ErrorCode.INVALID_REQUEST_BODY));
    }

    private static ResultMatcher[] apiError(ErrorCode errorCode) {
        return new ResultMatcher[] {
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                jsonPath("$.success").value(false),
                jsonPath("$.message").value(errorCode.getMessage()),
                jsonPath("$.data").value(nullValue()),
                jsonPath("$.error.code").value(errorCode.name()),
                jsonPath("$.error.details").isArray()
        };
    }

    private static class TestableGlobalExceptionHandler extends GlobalExceptionHandler {
        ResponseEntity<Object> handleInternalServerError(Exception exception, ServletWebRequest request) {
            return super.handleExceptionInternal(
                    exception, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request
            );
        }
    }

    @RestController
    @RequestMapping("/exception-test")
    static class ExceptionTestController {

        @GetMapping("/required")
        void required(@RequestParam String value) {
        }

        @PostMapping(value = "/content-type", consumes = MediaType.APPLICATION_JSON_VALUE)
        void contentType(@RequestBody String value) {
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/error")
        void error() {
            throw new IllegalStateException("test error");
        }

        @GetMapping("/status")
        void status(@RequestParam int status) {
            var exception = new ErrorResponseException(
                    HttpStatusCode.valueOf(status), new IOException("internal diagnostic")
            );
            exception.getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
            throw exception;
        }

        @GetMapping("/checked-error")
        void checkedError() throws IOException {
            throw new IOException("test checked error");
        }
    }

    record ValidationRequest(@NotBlank(message = "value는 필수입니다.") String value) {
    }
}
