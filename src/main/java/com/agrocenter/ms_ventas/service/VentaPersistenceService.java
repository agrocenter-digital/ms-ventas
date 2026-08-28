package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.dto.PaginaResponse;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.DetalleVenta;
import com.agrocenter.ms_ventas.entity.Venta;
import com.agrocenter.ms_ventas.exception.VentaNoEncontradaException;
import com.agrocenter.ms_ventas.mapper.VentaMapper;
import com.agrocenter.ms_ventas.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VentaPersistenceService {

    private final VentaRepository ventaRepository;
    private final VentaMapper mapper;

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> buscarPorIdempotencia(String clienteId, String key) {
        return ventaRepository.findByClienteIdAndIdempotencyKey(clienteId, key)
                .map(venta -> new IdempotencyRecord(mapper.toResponse(venta), venta.getRequestHash()));
    }

    @Transactional
    public VentaResponse crearPendiente(
            String clienteId,
            String idempotencyKey,
            String requestHash,
            List<DetallePreparado> items,
            BigDecimal subtotal,
            BigDecimal total
    ) {
        Venta venta = new Venta(clienteId, subtotal, total, idempotencyKey, requestHash);
        items.forEach(item -> venta.agregarDetalle(new DetalleVenta(
                item.productoId(),
                item.sku(),
                item.nombreProducto(),
                item.cantidad(),
                item.precioUnitario(),
                item.subtotal()
        )));
        return mapper.toResponse(ventaRepository.saveAndFlush(venta));
    }

    @Transactional
    public VentaResponse confirmar(Long ventaId) {
        Venta venta = buscarEntidad(ventaId);
        venta.confirmar();
        return mapper.toResponse(venta);
    }

    @Transactional
    public VentaResponse cancelar(Long ventaId, String motivo) {
        Venta venta = buscarEntidad(ventaId);
        venta.cancelar(motivo);
        return mapper.toResponse(venta);
    }

    @Transactional(readOnly = true)
    public VentaResponse obtener(Long ventaId) {
        return mapper.toResponse(buscarEntidad(ventaId));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<VentaResponse> listarCliente(String clienteId, int pagina, int tamanio) {
        Page<Venta> ventas = ventaRepository.findByClienteIdOrderByFechaCreacionDesc(
                clienteId,
                PageRequest.of(pagina, tamanio)
        );
        return PaginaResponse.desde(ventas, mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<VentaResponse> listarTodas(int pagina, int tamanio) {
        Page<Venta> ventas = ventaRepository.findAllByOrderByFechaCreacionDesc(
                PageRequest.of(pagina, tamanio)
        );
        return PaginaResponse.desde(ventas, mapper::toResponse);
    }

    private Venta buscarEntidad(Long ventaId) {
        return ventaRepository.findWithDetallesById(ventaId)
                .orElseThrow(VentaNoEncontradaException::new);
    }
}
