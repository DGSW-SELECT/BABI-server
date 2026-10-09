package com.select.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiError error
) {
    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, null);
    }
}
