package com.select.global.exception;

import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import com.select.global.response.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
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

        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(createErrorResponseBody(ErrorCode.VALIDATION_ERROR, details));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(createErrorResponseBody(ErrorCode.INVALID_REQUEST_BODY, List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(createErrorResponseBody(ErrorCode.METHOD_NOT_ALLOWED, List.of()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            RuntimeException e
    ) {
        log.error("서버 내부 오류가 발생했습니다.", e);

        return createErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                List.of()
        );
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
