package com.agrocenter.ms_ventas.mapper;

import com.agrocenter.ms_ventas.dto.DetalleVentaResponse;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.DetalleVenta;
import com.agrocenter.ms_ventas.entity.Venta;
import org.springframework.stereotype.Component;

@Component
public class VentaMapper {

    public VentaResponse toResponse(Venta venta) {
        return new VentaResponse(
                venta.getId(),
                venta.getClienteId(),
                venta.getFechaCreacion(),
                venta.getEstado(),
                venta.getSubtotal(),
                venta.getTotal(),
                venta.getMotivoCancelacion(),
                venta.getDetalles().stream().map(this::toDetalleResponse).toList(),
                venta.getCreatedAt(),
                venta.getUpdatedAt()
        );
    }

    private DetalleVentaResponse toDetalleResponse(DetalleVenta detalle) {
        return new DetalleVentaResponse(
                detalle.getId(),
                detalle.getProductoId(),
                detalle.getSku(),
                detalle.getNombreProducto(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal()
        );
    }
}
