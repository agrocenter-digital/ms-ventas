package com.agrocenter.ms_ventas.client.dto;

import java.time.Instant;

public record InventoryStockResponse(
        Long movimientoId,
        Long productoId,
        String sku,
        String tipoMovimiento,
        Integer cantidad,
        Integer stockAnterior,
        Integer stockPosterior,
        String referencia,
        boolean duplicada,
        Instant fecha
) {
}
