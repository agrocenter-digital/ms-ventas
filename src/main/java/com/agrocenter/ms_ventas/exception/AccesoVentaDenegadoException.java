package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class AccesoVentaDenegadoException extends ApiException {
    public AccesoVentaDenegadoException() {
        super(HttpStatus.FORBIDDEN, "SALE_ACCESS_DENIED", "No tiene acceso a esta venta");
    }
}
