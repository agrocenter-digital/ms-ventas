package com.agrocenter.ms_ventas.dto;

import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.entity.OrigenConsultaB2B;
import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoConsultaRequest(
        @NotNull EstadoConsultaB2B estado
) {
}
