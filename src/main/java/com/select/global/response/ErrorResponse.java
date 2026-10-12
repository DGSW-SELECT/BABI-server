package com.select.global.response;

import java.time.LocalDateTime;

public record ErrorResponse(
    String code,
    String message,
    LocalDateTime timeStamp
    ) {
    public static ErrorResponse of(
            String code,
            String message
    ) {
        return new ErrorResponse(
                code,
                message,
                LocalDateTime.now()
        );
    }

}
