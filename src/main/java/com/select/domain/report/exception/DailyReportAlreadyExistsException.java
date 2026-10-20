package com.select.domain.report.exception;

public class DailyReportAlreadyExistsException extends RuntimeException {

    public DailyReportAlreadyExistsException() {
        super("해당 날짜의 리포트가 이미 존재합니다.");
    }
}