package com.medicology.assessment.exception;

import java.time.Instant;

public record ErrorResponse(
        int status,
        int code,
        String message,
        String path,
        Instant timestamp
) {
}
