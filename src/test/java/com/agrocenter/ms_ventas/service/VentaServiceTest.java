package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.client.InventoryClient;
import com.agrocenter.ms_ventas.client.dto.InventoryAvailabilityResponse;
import com.agrocenter.ms_ventas.client.dto.InventoryProductResponse;
import com.agrocenter.ms_ventas.client.dto.InventoryStockResponse;
import com.agrocenter.ms_ventas.dto.CrearVentaRequest;
import com.agrocenter.ms_ventas.dto.ItemVentaRequest;
import com.agrocenter.ms_ventas.dto.PaginaResponse;
import com.agrocenter.ms_ventas.dto.VentaCreationResult;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.EstadoVenta;
import com.agrocenter.ms_ventas.exception.AccesoVentaDenegadoException;
import com.agrocenter.ms_ventas.exception.ConflictoIdempotenciaException;
import com.agrocenter.ms_ventas.exception.InventarioNoDisponibleException;
import com.agrocenter.ms_ventas.exception.SolicitudInvalidaException;
import com.agrocenter.ms_ventas.exception.StockInsuficienteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    private static final String CLIENTE = "cliente-123";
    private static final String TOKEN = "jwt-token";
    private static final String KEY = "checkout-001";
    private static final String HASH = "hash-request";
    private static final String CORRELATION = "corr-001";

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private VentaPersistenceService persistenceService;

    @Mock
    private RequestHasher requestHasher;

    private VentaService ventaService;

    @BeforeEach
    void setUp() {
        ventaService = new VentaService(inventoryClient, persistenceService, requestHasher);
    }

    @Test
    void creaVentaYCalculaTotalConPrecioConfiableDeInventario() {
        CrearVentaRequest request = request(new ItemVentaRequest(10L, 2));
        prepararCreacionBase(request);
        when(persistenceService.crearPendiente(
                eq(CLIENTE),
                eq(KEY),
                eq(HASH),
                any(),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenReturn(venta(77L, CLIENTE, EstadoVenta.PENDIENTE, "49980.00"));
        when(inventoryClient.descontarStock(10L, 2, "VENTA-77", TOKEN, CORRELATION))
                .thenReturn(stockResponse(10L, 2));
        when(persistenceService.confirmar(77L))
                .thenReturn(venta(77L, CLIENTE, EstadoVenta.CONFIRMADA, "49980.00"));

        VentaCreationResult result = ventaService.crear(
                CLIENTE,
                TOKEN,
                KEY,
                CORRELATION,
                request
        );

        assertThat(result.replay()).isFalse();
        assertThat(result.venta().estado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(result.venta().total()).isEqualByComparingTo("49980.00");
        ArgumentCaptor<BigDecimal> subtotal = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(persistenceService).crearPendiente(
                eq(CLIENTE),
                eq(KEY),
                eq(HASH),
                any(),
                subtotal.capture(),
                total.capture()
        );
        assertThat(subtotal.getValue()).isEqualByComparingTo("49980.00");
        assertThat(total.getValue()).isEqualByComparingTo("49980.00");
    }

    @Test
    void rechazaProductoDuplicadoAntesDeConsultarInventario() {
        CrearVentaRequest request = request(
                new ItemVentaRequest(10L, 1),
                new ItemVentaRequest(10L, 2)
        );

        assertThatThrownBy(() -> ventaService.crear(
                CLIENTE, TOKEN, KEY, CORRELATION, request
        )).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("duplicados");

        verify(inventoryClient, never()).consultarProducto(anyLong(), anyString(), anyString());
    }

    @Test
    void rechazaProductoSinStockSinCrearVenta() {
        CrearVentaRequest request = request(new ItemVentaRequest(10L, 20));
        when(requestHasher.hash(request.items())).thenReturn(HASH);
        when(persistenceService.buscarPorIdempotencia(CLIENTE, KEY)).thenReturn(Optional.empty());
        when(inventoryClient.consultarProducto(10L, TOKEN, CORRELATION))
                .thenReturn(producto(10L));
        when(inventoryClient.validarDisponibilidad(10L, 20, TOKEN, CORRELATION))
                .thenThrow(new StockInsuficienteException());

        assertThatThrownBy(() -> ventaService.crear(
                CLIENTE, TOKEN, KEY, CORRELATION, request
        )).isInstanceOf(StockInsuficienteException.class);

        verify(persistenceService, never()).crearPendiente(
                anyString(), anyString(), anyString(), any(), any(), any()
        );
    }

    @Test
    void cancelaVentaSiInventarioFallaAlDescontar() {
        CrearVentaRequest request = request(new ItemVentaRequest(10L, 2));
        prepararCreacionBase(request);
        when(persistenceService.crearPendiente(
                eq(CLIENTE), eq(KEY), eq(HASH), any(), any(), any()
        )).thenReturn(venta(77L, CLIENTE, EstadoVenta.PENDIENTE, "49980.00"));
        when(inventoryClient.descontarStock(10L, 2, "VENTA-77", TOKEN, CORRELATION))
                .thenThrow(new InventarioNoDisponibleException());

        assertThatThrownBy(() -> ventaService.crear(
                CLIENTE, TOKEN, KEY, CORRELATION, request
        )).isInstanceOf(InventarioNoDisponibleException.class);

        verify(persistenceService).cancelar(eq(77L), eq("No fue posible confirmar el descuento de inventario"));
        verify(persistenceService, never()).confirmar(anyLong());
    }

    @Test
    void compensaItemsDescontadosSiFallaUnItemPosterior() {
        CrearVentaRequest request = request(
                new ItemVentaRequest(10L, 2),
                new ItemVentaRequest(20L, 1)
        );
        when(requestHasher.hash(request.items())).thenReturn(HASH);
        when(persistenceService.buscarPorIdempotencia(CLIENTE, KEY)).thenReturn(Optional.empty());
        prepararProductoDisponible(10L, 2, "SEM-010", "Semilla", "100.00");
        prepararProductoDisponible(20L, 1, "FER-020", "Fertilizante", "200.00");
        when(persistenceService.crearPendiente(
                eq(CLIENTE), eq(KEY), eq(HASH), any(), any(), any()
        )).thenReturn(venta(88L, CLIENTE, EstadoVenta.PENDIENTE, "400.00"));
        when(inventoryClient.descontarStock(10L, 2, "VENTA-88", TOKEN, CORRELATION))
                .thenReturn(stockResponse(10L, 2));
        when(inventoryClient.descontarStock(20L, 1, "VENTA-88", TOKEN, CORRELATION))
                .thenThrow(new StockInsuficienteException());
        when(inventoryClient.compensarStock(
                10L, 2, "COMPENSACION-VENTA-88", TOKEN, CORRELATION
        )).thenReturn(stockResponse(10L, 2));

        assertThatThrownBy(() -> ventaService.crear(
                CLIENTE, TOKEN, KEY, CORRELATION, request
        )).isInstanceOf(StockInsuficienteException.class);

        verify(inventoryClient).compensarStock(
                10L, 2, "COMPENSACION-VENTA-88", TOKEN, CORRELATION
        );
        verify(persistenceService).cancelar(
                88L,
                "No fue posible confirmar el descuento de inventario"
        );
    }

    @Test
    void repiteMismaSolicitudSinCrearOtraVenta() {
        CrearVentaRequest request = request(new ItemVentaRequest(10L, 2));
        VentaResponse existente = venta(77L, CLIENTE, EstadoVenta.CONFIRMADA, "49980.00");
        when(requestHasher.hash(request.items())).thenReturn(HASH);
        when(persistenceService.buscarPorIdempotencia(CLIENTE, KEY))
                .thenReturn(Optional.of(new IdempotencyRecord(existente, HASH)));

        VentaCreationResult result = ventaService.crear(
                CLIENTE, TOKEN, KEY, CORRELATION, request
        );

        assertThat(result.replay()).isTrue();
        assertThat(result.venta().id()).isEqualTo(77L);
        verify(inventoryClient, never()).consultarProducto(anyLong(), anyString(), anyString());
    }

    @Test
    void rechazaReutilizarClaveConOtroContenido() {
        CrearVentaRequest request = request(new ItemVentaRequest(10L, 2));
        when(requestHasher.hash(request.items())).thenReturn("hash-nuevo");
        when(persistenceService.buscarPorIdempotencia(CLIENTE, KEY)).thenReturn(Optional.of(
                new IdempotencyRecord(
                        venta(77L, CLIENTE, EstadoVenta.CONFIRMADA, "49980.00"),
                        "hash-original"
                )
        ));

        assertThatThrownBy(() -> ventaService.crear(
                CLIENTE, TOKEN, KEY, CORRELATION, request
        )).isInstanceOf(ConflictoIdempotenciaException.class);
    }

    @Test
    void impideAccesoAUnaVentaDeOtroCliente() {
        when(persistenceService.obtener(77L))
                .thenReturn(venta(77L, "otro-cliente", EstadoVenta.CONFIRMADA, "100.00"));

        assertThatThrownBy(() -> ventaService.obtener(77L, CLIENTE, false))
                .isInstanceOf(AccesoVentaDenegadoException.class);
    }

    @Test
    void consultaVentasDelClienteAutenticado() {
        PaginaResponse<VentaResponse> page = new PaginaResponse<>(List.of(), 0, 20, 0, 0);
        when(persistenceService.listarCliente(CLIENTE, 0, 20)).thenReturn(page);

        assertThat(ventaService.listarCliente(CLIENTE, 0, 20)).isSameAs(page);
    }

    private void prepararCreacionBase(CrearVentaRequest request) {
        when(requestHasher.hash(request.items())).thenReturn(HASH);
        when(persistenceService.buscarPorIdempotencia(CLIENTE, KEY)).thenReturn(Optional.empty());
        prepararProductoDisponible(10L, 2, "SEM-001", "Semilla de maiz", "24990.00");
    }

    private void prepararProductoDisponible(
            Long id,
            int cantidad,
            String sku,
            String nombre,
            String precio
    ) {
        when(inventoryClient.consultarProducto(id, TOKEN, CORRELATION))
                .thenReturn(new InventoryProductResponse(
                        id, sku, nombre, new BigDecimal(precio), 100, true
                ));
        when(inventoryClient.validarDisponibilidad(id, cantidad, TOKEN, CORRELATION))
                .thenReturn(new InventoryAvailabilityResponse(id, true, 100, cantidad));
    }

    private InventoryProductResponse producto(Long id) {
        return new InventoryProductResponse(
                id,
                "SEM-001",
                "Semilla de maiz",
                new BigDecimal("24990.00"),
                10,
                true
        );
    }

    private InventoryStockResponse stockResponse(Long productoId, int cantidad) {
        return new InventoryStockResponse(
                1L,
                productoId,
                "SKU",
                "SALIDA",
                cantidad,
                10,
                10 - cantidad,
                "VENTA",
                false,
                Instant.now()
        );
    }

    private CrearVentaRequest request(ItemVentaRequest... items) {
        return new CrearVentaRequest(List.of(items));
    }

    private VentaResponse venta(
            Long id,
            String cliente,
            EstadoVenta estado,
            String total
    ) {
        Instant now = Instant.parse("2026-08-28T12:00:00Z");
        return new VentaResponse(
                id,
                cliente,
                now,
                estado,
                new BigDecimal(total),
                new BigDecimal(total),
                estado == EstadoVenta.CANCELADA ? "Cancelada" : null,
                List.of(),
                now,
                now
        );
    }
}
