package com.agrocenter.ms_ventas.dto;

import com.agrocenter.ms_ventas.entity.ConsultaB2B;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.entity.OrigenConsultaB2B;

import java.time.Instant;

public record ConsultaB2BResponse(
        Long id,
        String skuProducto,
        String nombreProducto,
        Integer cantidad,
        String nombreCliente,
        String correoContacto,
        String mensaje,
        OrigenConsultaB2B origen,
        EstadoConsultaB2B estado,
        Instant fechaCreacion
) {
    public static ConsultaB2BResponse desde(ConsultaB2B consulta) {
        return new ConsultaB2BResponse(
                consulta.getId(),
                consulta.getSkuProducto(),
                consulta.getNombreProducto(),
                consulta.getCantidad(),
                consulta.getNombreCliente(),
                consulta.getCorreoContacto(),
                consulta.getMensaje(),
                consulta.getOrigen(),
                consulta.getEstado(),
                consulta.getFechaCreacion()
        );
    }
}
