package com.agrocenter.ms_ventas.messaging.audit;

import java.time.Instant;

public record TrazaEvento(
        long id,
        Instant fecha,
        String servicio,
        String topico,
        String direccion,
        String clave,
        String estado,
        String detalle
) {
}
