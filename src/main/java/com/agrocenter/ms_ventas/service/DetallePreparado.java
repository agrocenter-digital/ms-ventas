package com.agrocenter.ms_ventas.service;

import java.math.BigDecimal;

record DetallePreparado(
        Long productoId,
        String sku,
        String nombreProducto,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {
}
