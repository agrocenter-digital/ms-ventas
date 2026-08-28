package com.agrocenter.ms_ventas.dto;

import com.agrocenter.ms_ventas.entity.EstadoVenta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VentaResponse(
        Long id,
        String clienteId,
        Instant fechaCreacion,
        EstadoVenta estado,
        BigDecimal subtotal,
        BigDecimal total,
        String motivoCancelacion,
        List<DetalleVentaResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
