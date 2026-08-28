package com.agrocenter.ms_ventas.client.dto;

public record InventoryAvailabilityResponse(
        Long productoId,
        boolean disponible,
        Integer stockActual,
        Integer cantidadSolicitada
) {
}
