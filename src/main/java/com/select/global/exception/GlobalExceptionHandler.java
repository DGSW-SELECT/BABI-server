package com.select.global.exception;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.exception.InvalidOwnershipTransferException;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.exception.InvalidCredentialsException;
import com.select.domain.user.exception.PasswordMismatchException;
import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import com.select.global.response.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailVerification(EmailVerificationException e) {
        return createErrorResponse(e.getErrorCode(), List.of());
    }

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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
            InvalidCredentialsException e
    ) {
        return createErrorResponse(
                ErrorCode.INVALID_CREDENTIALS,
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException e
    ) {
        return createErrorResponse(
                ErrorCode.FORBIDDEN,
                List.of()
        );
    }

    @ExceptionHandler(BabyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBabyNotFound(
            BabyNotFoundException e
    ) {
        return createErrorResponse(
                ErrorCode.NOT_FOUND,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e
    ) {
        return createErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                List.of(new ErrorDetail(
                        e.getName(),
                        "요청한 값의 형식이 올바르지 않습니다.")
                )
        );
    }

    @ExceptionHandler(InvalidOwnershipTransferException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOwnershipTransfer(
            InvalidOwnershipTransferException e
    ) {
        return createErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                List.of()
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
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
        return handleExceptionInternal(
                e, createErrorResponseBody(ErrorCode.VALIDATION_ERROR, details),
                headers, status, request
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return handleExceptionInternal(
                e, createErrorResponseBody(ErrorCode.INVALID_REQUEST_BODY, List.of()),
                headers, status, request
        );
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Object responseBody = body instanceof ApiResponse<?> ? body : createErrorResponseBody(resolveErrorCode(status), List.of());
        return super.createResponseEntity(responseBody, headers, status, request);
    }

    private ErrorCode resolveErrorCode(HttpStatusCode status) {
        return switch (HttpStatus.resolve(status.value())) {
            case BAD_REQUEST -> ErrorCode.VALIDATION_ERROR;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case null, default -> status.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.REQUEST_ERROR;
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception e
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
