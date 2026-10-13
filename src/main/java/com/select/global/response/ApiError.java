package com.select.global.response;

import java.util.List;

public record ApiError(
        String code,
        List<ErrorDetail> details
) {
}
