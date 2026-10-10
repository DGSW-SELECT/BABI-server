package com.select.global.exception;

import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import com.select.global.response.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(
            DuplicateEmailException e
    ) {
        ErrorDetail detail = new ErrorDetail("email", e.getMessage());
        ApiError error = new ApiError(
                "DUPLICATE_EMAIL",
                List.of(detail)
        );
        ApiResponse<Void> response = new ApiResponse<>(false, e.getMessage(), null, error);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordMismatch(
            PasswordMismatchException e
    ) {
        ErrorDetail detail = new ErrorDetail("passwordConfirm", e.getMessage());
        ApiError error = new ApiError(
                "PASSWORD_MISMATCH",
                List.of(detail)
        );
        ApiResponse<Void> response = new ApiResponse<>(false, e.getMessage(), null, error);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException e
    ) {
        List<ErrorDetail> details = new ArrayList<>();

        e.getBindingResult().getFieldErrors().forEach(error ->
                details.add(new ErrorDetail(
                        error.getField(),
                        error.getDefaultMessage()
                ))
        );

        e.getBindingResult().getGlobalErrors().forEach(error ->
                details.add(new ErrorDetail(
                        error.getObjectName(),
                        error.getDefaultMessage()
                ))
        );

        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                details
        );
        ApiResponse<Void> response = new ApiResponse<>(false, "입력값을 확인해 주세요.", null, error);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e
    ) {
        log.error("서버 내부 오류가 발생했습니다.", e);
        ApiResponse<Void> response = new ApiResponse<>(false, "서버에 오류가 발생했습니다.", null, new ApiError("INTERNAL_SERVER_ERROR", List.of()));
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(
            HttpMessageNotReadableException e
    ) {
        ApiError error = new ApiError(
                "INVALID_REQUEST_BODY",
                List.of()
        );
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                "요청 본문이 올바르지 않습니다.",
                null,
                error
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e
    ) {
        ApiError error = new ApiError(
                "METHOD_NOT_ALLOWED",
                List.of()
        );

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                "지원하지 않는 HTTP 메서드 입니다.",
                null,
                error
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(e.getHeaders())
                .body(response);
    }
}
