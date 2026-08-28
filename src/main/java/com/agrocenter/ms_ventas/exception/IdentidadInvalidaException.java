package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class IdentidadInvalidaException extends ApiException {
    public IdentidadInvalidaException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "INVALID_IDENTITY",
                "El JWT no contiene una identidad de cliente valida"
        );
    }
}
