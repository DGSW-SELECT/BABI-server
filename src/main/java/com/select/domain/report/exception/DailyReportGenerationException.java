package com.select.domain.report.exception;

import com.select.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class DailyReportGenerationException extends RuntimeException {
    private final ErrorCode errorCode;

    public DailyReportGenerationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
