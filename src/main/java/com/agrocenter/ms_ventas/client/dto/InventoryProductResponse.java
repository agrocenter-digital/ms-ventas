package com.agrocenter.ms_ventas.client.dto;

import java.math.BigDecimal;

public record InventoryProductResponse(
        Long id,
        String sku,
        String nombre,
        BigDecimal precioVenta,
        Integer stockActual,
        boolean activo,
        boolean soloCotizacion
) {
    public InventoryProductResponse(Long id, String sku, String nombre, BigDecimal precioVenta,
            Integer stockActual, boolean activo) {
        this(id, sku, nombre, precioVenta, stockActual, activo, false);
    }

}
