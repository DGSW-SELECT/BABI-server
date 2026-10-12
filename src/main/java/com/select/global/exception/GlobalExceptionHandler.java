package com.select.global.exception;

import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import com.select.global.response.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return createErrorResponse(
                ErrorCode.DUPLICATE_EMAIL,
                List.of(new ErrorDetail("email", ErrorCode.DUPLICATE_EMAIL.getMessage()))
        );
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordMismatch(
            PasswordMismatchException e
    ) {
        return createErrorResponse(
                ErrorCode.PASSWORD_MISMATCH,
                List.of(new ErrorDetail("passwordConfirm", ErrorCode.PASSWORD_MISMATCH.getMessage()))
        );
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

        return createErrorResponse(ErrorCode.VALIDATION_ERROR, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e
    ) {
        log.error("서버 내부 오류가 발생했습니다.", e);
        return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(
            HttpMessageNotReadableException e
    ) {
        return createErrorResponse(ErrorCode.INVALID_REQUEST_BODY, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e
    ) {
        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .headers(e.getHeaders())
                .body(createErrorResponseBody(ErrorCode.METHOD_NOT_ALLOWED, List.of()));
    }

    private ResponseEntity<ApiResponse<Void>> createErrorResponse(
            ErrorCode errorCode,
            List<ErrorDetail> details
    ) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(createErrorResponseBody(errorCode, details));
    }

    private ApiResponse<Void> createErrorResponseBody(
            ErrorCode errorCode,
            List<ErrorDetail> details
    ) {
        ApiError error = new ApiError(errorCode.name(), details);
        return new ApiResponse<>(false, errorCode.getMessage(), null, error);
    }
}
