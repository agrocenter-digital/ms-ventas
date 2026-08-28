package com.agrocenter.ms_ventas.client;

import com.agrocenter.ms_ventas.client.dto.InventoryProductResponse;
import com.agrocenter.ms_ventas.config.InventoryProperties;
import com.agrocenter.ms_ventas.exception.InventarioNoDisponibleException;
import com.agrocenter.ms_ventas.exception.ProductoNoEncontradoException;
import com.agrocenter.ms_ventas.exception.StockInsuficienteException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryClientTest {

    private HttpServer server;
    private volatile StubResponse stubResponse;
    private volatile String lastPath;
    private volatile String lastAuthorization;
    private volatile String lastCorrelationId;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void procesaRespuesta200YPropagaEncabezados() {
        respond(200, """
                {
                  "id": 10,
                  "sku": "SEM-010",
                  "nombre": "Semilla",
                  "precioVenta": 1250.50,
                  "stockActual": 8,
                  "activo": true
                }
                """, Duration.ZERO);

        InventoryProductResponse response = client(Duration.ofSeconds(1))
                .consultarProducto(10L, "jwt-cliente", "corr-123");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.precioVenta()).isEqualByComparingTo("1250.50");
        assertThat(lastPath).isEqualTo("/api/inventario/productos/10");
        assertThat(lastAuthorization).isEqualTo("Bearer jwt-cliente");
        assertThat(lastCorrelationId).isEqualTo("corr-123");
    }

    @Test
    void traduce404AProductoNoEncontrado() {
        respond(404, "{\"message\":\"Producto no encontrado\"}", Duration.ZERO);

        assertThatThrownBy(() -> client(Duration.ofSeconds(1))
                .consultarProducto(99L, "jwt", "corr"))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void traduce409AStockInsuficiente() {
        respond(409, "{\"message\":\"Stock insuficiente\"}", Duration.ZERO);

        assertThatThrownBy(() -> client(Duration.ofSeconds(1))
                .descontarStock(10L, 20, "VENTA-1", "jwt", "corr"))
                .isInstanceOf(StockInsuficienteException.class);
    }

    @Test
    void traduce500AServicioNoDisponible() {
        respond(500, "{\"message\":\"Error interno\"}", Duration.ZERO);

        assertThatThrownBy(() -> client(Duration.ofSeconds(1))
                .validarDisponibilidad(10L, 1, "jwt", "corr"))
                .isInstanceOf(InventarioNoDisponibleException.class);
    }

    @Test
    void cortaLaEsperaAlSuperarElTimeout() {
        respond(200, """
                {
                  "productoId": 10,
                  "disponible": true,
                  "stockActual": 8,
                  "cantidadSolicitada": 1
                }
                """, Duration.ofMillis(300));

        assertThatThrownBy(() -> client(Duration.ofMillis(50))
                .validarDisponibilidad(10L, 1, "jwt", "corr"))
                .isInstanceOf(InventarioNoDisponibleException.class);
    }

    private InventoryClient client(Duration readTimeout) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        InventoryProperties properties = new InventoryProperties(
                baseUrl,
                Duration.ofSeconds(1),
                readTimeout,
                ""
        );
        return new InventoryClient(WebClient.builder().baseUrl(baseUrl).build(), properties);
    }

    private void respond(int status, String body, Duration delay) {
        this.stubResponse = new StubResponse(status, body, delay);
    }

    private void handle(HttpExchange exchange) throws IOException {
        StubResponse current = stubResponse;
        lastPath = exchange.getRequestURI().getPath();
        lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        lastCorrelationId = exchange.getRequestHeaders().getFirst("X-Correlation-ID");
        exchange.getRequestBody().readAllBytes();
        try {
            Thread.sleep(current.delay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        byte[] responseBody = current.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(current.status(), responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private record StubResponse(int status, String body, Duration delay) {
    }
}
