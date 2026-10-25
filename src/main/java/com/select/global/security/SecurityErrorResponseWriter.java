package com.select.global.security;

import com.select.global.exception.ErrorCode;
import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        if (errorCode.getHttpStatus().value() == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        ApiError error = new ApiError(errorCode.name(), List.of());
        ApiResponse<Void> apiResponse = new ApiResponse<>(false,errorCode.getMessage(),null,error);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),apiResponse);
    }

}
