package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.client.InventoryClient;
import com.agrocenter.ms_ventas.client.dto.InventoryProductResponse;
import com.agrocenter.ms_ventas.dto.CrearVentaRequest;
import com.agrocenter.ms_ventas.dto.ItemVentaRequest;
import com.agrocenter.ms_ventas.dto.PaginaResponse;
import com.agrocenter.ms_ventas.dto.VentaCreationResult;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.exception.AccesoVentaDenegadoException;
import com.agrocenter.ms_ventas.exception.ApiException;
import com.agrocenter.ms_ventas.exception.ConflictoIdempotenciaException;
import com.agrocenter.ms_ventas.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VentaService {

    private static final int MONEY_SCALE = 2;
    private static final String CANCELLATION_REASON = "No fue posible confirmar el descuento de inventario";

    private final InventoryClient inventoryClient;
    private final VentaPersistenceService persistenceService;
    private final RequestHasher requestHasher;

    public VentaCreationResult crear(
            String clienteId,
            String bearerToken,
            String idempotencyKey,
            String correlationId,
            CrearVentaRequest request
    ) {
        validarProductosDuplicados(request.items());
        String key = idempotencyKey.trim();
        String requestHash = requestHasher.hash(request.items());

        Optional<IdempotencyRecord> existente = persistenceService.buscarPorIdempotencia(clienteId, key);
        if (existente.isPresent()) {
            return replay(existente.get(), requestHash);
        }

        List<DetallePreparado> detalles = prepararDetalles(
                request.items(),
                bearerToken,
                correlationId
        );
        BigDecimal subtotal = detalles.stream()
                .map(DetallePreparado::subtotal)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add);

        VentaResponse pendiente;
        try {
            pendiente = persistenceService.crearPendiente(
                    clienteId,
                    key,
                    requestHash,
                    detalles,
                    subtotal,
                    subtotal
            );
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecord ganadora = persistenceService.buscarPorIdempotencia(clienteId, key)
                    .orElseThrow(() -> exception);
            return replay(ganadora, requestHash);
        }

        log.info(
                "Venta pendiente creada ventaId={} clienteId={} correlationId={} estado={}",
                pendiente.id(),
                clienteId,
                correlationId,
                pendiente.estado()
        );

        List<DetallePreparado> descontados = new ArrayList<>();
        try {
            String referencia = "VENTA-" + pendiente.id();
            for (DetallePreparado detalle : detalles) {
                inventoryClient.descontarStock(
                        detalle.productoId(),
                        detalle.cantidad(),
                        referencia,
                        bearerToken,
                        correlationId
                );
                descontados.add(detalle);
            }
            VentaResponse confirmada = persistenceService.confirmar(pendiente.id());
            log.info(
                    "Venta confirmada ventaId={} clienteId={} correlationId={} estado={}",
                    confirmada.id(),
                    clienteId,
                    correlationId,
                    confirmada.estado()
            );
            return new VentaCreationResult(confirmada, false);
        } catch (ApiException exception) {
            cancelarYCompensar(pendiente.id(), clienteId, descontados, bearerToken, correlationId);
            throw exception;
        } catch (RuntimeException exception) {
            cancelarYCompensar(pendiente.id(), clienteId, descontados, bearerToken, correlationId);
            throw exception;
        }
    }

    public VentaResponse obtener(Long ventaId, String clienteId, boolean admin) {
        VentaResponse venta = persistenceService.obtener(ventaId);
        if (!admin && !venta.clienteId().equals(clienteId)) {
            throw new AccesoVentaDenegadoException();
        }
        return venta;
    }

    public PaginaResponse<VentaResponse> listarCliente(
            String clienteId,
            int pagina,
            int tamanio
    ) {
        return persistenceService.listarCliente(clienteId, pagina, tamanio);
    }

    public PaginaResponse<VentaResponse> listarTodas(int pagina, int tamanio) {
        return persistenceService.listarTodas(pagina, tamanio);
    }

    private List<DetallePreparado> prepararDetalles(
            List<ItemVentaRequest> items,
            String bearerToken,
            String correlationId
    ) {
        List<DetallePreparado> detalles = new ArrayList<>();
        for (ItemVentaRequest item : items) {
            InventoryProductResponse producto = inventoryClient.consultarProducto(
                    item.productoId(),
                    bearerToken,
                    correlationId
            );
            validarProducto(producto, item.productoId());
            inventoryClient.validarDisponibilidad(
                    item.productoId(),
                    item.cantidad(),
                    bearerToken,
                    correlationId
            );
            BigDecimal precio = producto.precioVenta().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(item.cantidad()))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            detalles.add(new DetallePreparado(
                    producto.id(),
                    producto.sku(),
                    producto.nombre(),
                    item.cantidad(),
                    precio,
                    subtotal
            ));
        }
        return detalles;
    }

    private void validarProducto(InventoryProductResponse producto, Long requestedId) {
        if (producto == null
                || !requestedId.equals(producto.id())
                || producto.sku() == null
                || producto.sku().isBlank()
                || producto.nombre() == null
                || producto.nombre().isBlank()
                || producto.precioVenta() == null
                || producto.precioVenta().signum() < 0
                || !producto.activo()) {
            throw new com.agrocenter.ms_ventas.exception.InventarioNoDisponibleException();
        }
    }

    private void validarProductosDuplicados(List<ItemVentaRequest> items) {
        Set<Long> productos = new HashSet<>();
        boolean duplicado = items.stream().anyMatch(item -> !productos.add(item.productoId()));
        if (duplicado) {
            throw new SolicitudInvalidaException(
                    "DUPLICATE_PRODUCT",
                    "No se permiten productos duplicados en una venta"
            );
        }
    }

    private VentaCreationResult replay(IdempotencyRecord existente, String requestHash) {
        if (!requestHash.equals(existente.requestHash())) {
            throw new ConflictoIdempotenciaException();
        }
        return new VentaCreationResult(existente.venta(), true);
    }

    private boolean compensar(
            Long ventaId,
            List<DetallePreparado> descontados,
            String bearerToken,
            String correlationId
    ) {
        boolean completa = true;
        String referencia = "COMPENSACION-VENTA-" + ventaId;
        for (int index = descontados.size() - 1; index >= 0; index--) {
            DetallePreparado detalle = descontados.get(index);
            try {
                inventoryClient.compensarStock(
                        detalle.productoId(),
                        detalle.cantidad(),
                        referencia,
                        bearerToken,
                        correlationId
                );
            } catch (ApiException exception) {
                completa = false;
                log.error(
                        "Compensacion de inventario pendiente ventaId={} productoId={} correlationId={}",
                        ventaId,
                        detalle.productoId(),
                        correlationId
                );
            }
        }
        return completa;
    }

    private void cancelarYCompensar(
            Long ventaId,
            String clienteId,
            List<DetallePreparado> descontados,
            String bearerToken,
            String correlationId
    ) {
        boolean compensada = compensar(
                ventaId,
                descontados,
                bearerToken,
                correlationId
        );
        String motivo = compensada
                ? CANCELLATION_REASON
                : CANCELLATION_REASON + "; compensacion pendiente de revision";
        try {
            persistenceService.cancelar(ventaId, motivo);
        } catch (RuntimeException cancellationException) {
            log.error(
                    "No fue posible persistir la cancelacion ventaId={} correlationId={}",
                    ventaId,
                    correlationId,
                    cancellationException
            );
        }
        log.warn(
                "Venta cancelada ventaId={} clienteId={} correlationId={} compensada={}",
                ventaId,
                clienteId,
                correlationId,
                compensada
        );
    }

}
