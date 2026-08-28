package com.agrocenter.ms_ventas.client.dto;

public record InventoryStockRequest(Long productoId, Integer cantidad, String referencia) {
}
