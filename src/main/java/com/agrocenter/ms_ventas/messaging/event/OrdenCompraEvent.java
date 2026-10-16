package com.agrocenter.ms_ventas.messaging.event;

import com.agrocenter.ms_ventas.dto.DetalleVentaResponse;
import com.agrocenter.ms_ventas.dto.VentaResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrdenCompraEvent(
        Long ventaId,
        String clienteId,
        Instant fechaCreacion,
        BigDecimal total,
        List<ItemOrdenCompraEvent> items
) {

    public record ItemOrdenCompraEvent(
            Long productoId,
            String sku,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {
    }

    public static OrdenCompraEvent desde(VentaResponse venta) {
        List<ItemOrdenCompraEvent> items = venta.items().stream()
                .map(OrdenCompraEvent::toItem)
                .toList();
        return new OrdenCompraEvent(
                venta.id(),
                venta.clienteId(),
                venta.fechaCreacion(),
                venta.total(),
                items
        );
    }

    private static ItemOrdenCompraEvent toItem(DetalleVentaResponse detalle) {
        return new ItemOrdenCompraEvent(
                detalle.productoId(),
                detalle.sku(),
                detalle.cantidad(),
                detalle.precioUnitario(),
                detalle.subtotal()
        );
    }
}
