package com.select.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    @Test
    void handleExceptionReturns500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleException(
                new RuntimeException("테스트 오류")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        var body = response.getBody();

        assertNotNull(body);
        assertEquals("INTERNAL_SERVER_ERROR", body.error().code());
        assertTrue(body.error().details().isEmpty());
        assertFalse(body.success());
        assertEquals("서버에 오류가 발생했습니다.", body.message());
        assertNull(body.data());
        assertNotNull(body.error());
    }
}
