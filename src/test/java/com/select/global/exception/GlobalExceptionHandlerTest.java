package com.select.global.exception;

import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        assertEquals("INTERNAL_SERVER_ERROR", body.error().code());
        assertTrue(body.error().details().isEmpty());
        assertFalse(body.success());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), body.message());
        assertNull(body.data());
        assertNotNull(body.error());
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingRequiredParameterReturns400() throws Exception {
        mockMvc.perform(get("/exception-test/required"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/exception-test/content-type")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/exception-test/content-type"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void validationFailureReturns400() throws Exception {
        mockMvc.perform(post("/exception-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unexpectedServerErrorReturns500() throws Exception {
        mockMvc.perform(get("/exception-test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
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
    }

    record ValidationRequest(@NotBlank String value) {
    }
}
