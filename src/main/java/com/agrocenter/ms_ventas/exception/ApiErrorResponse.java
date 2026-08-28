package com.agrocenter.ms_ventas.exception;

import com.agrocenter.ms_ventas.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> validationErrors
) {
    public static ApiErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                error,
                code,
                message,
                request.getRequestURI(),
                CorrelationIdFilter.from(request),
                Map.of()
        );
    }
}
