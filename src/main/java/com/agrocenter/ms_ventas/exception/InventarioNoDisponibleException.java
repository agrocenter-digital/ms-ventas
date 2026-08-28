package com.agrocenter.ms_ventas.exception;

import org.springframework.http.HttpStatus;

public class InventarioNoDisponibleException extends ApiException {
    public InventarioNoDisponibleException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INVENTORY_UNAVAILABLE",
                "El servicio de inventario no esta disponible temporalmente"
        );
    }
}
