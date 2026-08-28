package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class VentaNoEncontradaException extends ApiException {
    public VentaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", "Venta no encontrada");
    }
}
