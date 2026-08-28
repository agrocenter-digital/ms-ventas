package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class ConflictoIdempotenciaException extends ApiException {
    public ConflictoIdempotenciaException() {
        super(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                "La clave de idempotencia ya fue utilizada con una solicitud diferente"
        );
    }
}
