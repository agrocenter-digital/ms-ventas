package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class ConsultaB2BNoEncontradaException extends ApiException {
    public ConsultaB2BNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "CONSULTA_NOT_FOUND", "Consulta B2B no encontrada");
    }
}
