package ru.vstu.medsim.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId
) {

    public static ApiErrorResponse of(
            HttpStatusCode statusCode,
            String message,
            String path,
            String requestId
    ) {
        HttpStatus httpStatus = HttpStatus.resolve(statusCode.value());
        String error = httpStatus != null ? httpStatus.getReasonPhrase() : "HTTP " + statusCode.value();

        return new ApiErrorResponse(
                Instant.now(),
                statusCode.value(),
                error,
                message,
                path,
                requestId
        );
    }
}
