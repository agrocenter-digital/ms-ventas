package com.agrocenter.ms_ventas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemVentaRequest(
        @NotNull @Positive Long productoId,
        @NotNull @Positive Integer cantidad
) {
}
