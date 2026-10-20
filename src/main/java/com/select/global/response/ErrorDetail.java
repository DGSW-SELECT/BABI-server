package com.select.global.response;

public record ErrorDetail(
        String field,
        String reason
) {
}
