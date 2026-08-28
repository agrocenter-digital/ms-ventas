package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class StockInsuficienteException extends ApiException {
    public StockInsuficienteException() {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                "Stock insuficiente para uno o mas productos"
        );
    }
}
