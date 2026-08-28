package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class ProductoNoEncontradoException extends ApiException {
    public ProductoNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Producto no encontrado");
    }
}
