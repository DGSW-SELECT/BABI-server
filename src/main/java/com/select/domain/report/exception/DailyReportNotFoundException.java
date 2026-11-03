package com.select.domain.report.exception;

public class DailyReportNotFoundException extends RuntimeException {

    public DailyReportNotFoundException() {
        super("해당 날짜의 리포트를 찾을 수 없습니다.");
    }
}