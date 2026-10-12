package com.select.global.exception;

import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import com.select.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException e
    ) {
        List<ErrorDetail> details = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorDetail(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                details
        );
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                "입력값을 확인해 주세요.",
                null,
                error
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
