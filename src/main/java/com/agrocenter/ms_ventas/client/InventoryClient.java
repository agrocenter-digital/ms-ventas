package com.agrocenter.ms_ventas.client;

import com.agrocenter.ms_ventas.client.dto.InventoryAvailabilityRequest;
import com.agrocenter.ms_ventas.client.dto.InventoryAvailabilityResponse;
import com.agrocenter.ms_ventas.client.dto.InventoryErrorResponse;
import com.agrocenter.ms_ventas.client.dto.InventoryProductResponse;
import com.agrocenter.ms_ventas.client.dto.InventoryStockRequest;
import com.agrocenter.ms_ventas.client.dto.InventoryStockResponse;
import com.agrocenter.ms_ventas.config.InventoryProperties;
import com.agrocenter.ms_ventas.exception.ApiException;
import com.agrocenter.ms_ventas.exception.InventarioNoDisponibleException;
import com.agrocenter.ms_ventas.exception.ProductoNoEncontradoException;
import com.agrocenter.ms_ventas.exception.StockInsuficienteException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class InventoryClient {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final WebClient webClient;
    private final InventoryProperties properties;

    public InventoryClient(WebClient inventoryWebClient, InventoryProperties properties) {
        this.webClient = inventoryWebClient;
        this.properties = properties;
    }

    public InventoryProductResponse consultarProducto(
            Long productoId,
            String forwardedToken,
            String correlationId
    ) {
        return ejecutar(
                webClient.get()
                        .uri("/api/inventario/productos/{id}", productoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(forwardedToken))
                        .header(CORRELATION_HEADER, correlationId)
                        .exchangeToMono(response -> leerRespuesta(
                                response,
                                InventoryProductResponse.class,
                                true
                        )),
                "consultar producto"
        );
    }

    public InventoryAvailabilityResponse validarDisponibilidad(
            Long productoId,
            Integer cantidad,
            String forwardedToken,
            String correlationId
    ) {
        InventoryAvailabilityResponse response = ejecutar(
                webClient.post()
                        .uri("/api/inventario/stock/validar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(forwardedToken))
                        .header(CORRELATION_HEADER, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new InventoryAvailabilityRequest(productoId, cantidad))
                        .exchangeToMono(clientResponse -> leerRespuesta(
                                clientResponse,
                                InventoryAvailabilityResponse.class,
                                true
                        )),
                "validar stock"
        );
        if (!response.disponible()) {
            throw new StockInsuficienteException();
        }
        return response;
    }

    public InventoryStockResponse descontarStock(
            Long productoId,
            Integer cantidad,
            String referencia,
            String forwardedToken,
            String correlationId
    ) {
        return operacionStock(
                "/api/inventario/stock/salida",
                new InventoryStockRequest(productoId, cantidad, referencia),
                forwardedToken,
                correlationId,
                "descontar stock"
        );
    }

    public InventoryStockResponse compensarStock(
            Long productoId,
            Integer cantidad,
            String referencia,
            String forwardedToken,
            String correlationId
    ) {
        return operacionStock(
                "/api/inventario/stock/entrada",
                new InventoryStockRequest(productoId, cantidad, referencia),
                forwardedToken,
                correlationId,
                "compensar stock"
        );
    }

    private InventoryStockResponse operacionStock(
            String path,
            InventoryStockRequest request,
            String forwardedToken,
            String correlationId,
            String operation
    ) {
        return ejecutar(
                webClient.post()
                        .uri(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(forwardedToken))
                        .header(CORRELATION_HEADER, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchangeToMono(response -> leerRespuesta(
                                response,
                                InventoryStockResponse.class,
                                true
                        )),
                operation
        );
    }

    private <T> Mono<T> leerRespuesta(
            ClientResponse response,
            Class<T> responseType,
            boolean mapNotFound
    ) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(responseType);
        }

        return response.bodyToMono(InventoryErrorResponse.class)
                .onErrorReturn(new InventoryErrorResponse(null))
                .defaultIfEmpty(new InventoryErrorResponse(null))
                .flatMap(error -> {
                    if (mapNotFound && response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        return Mono.error(new ProductoNoEncontradoException());
                    }
                    if (response.statusCode().value() == HttpStatus.CONFLICT.value()) {
                        return Mono.error(new StockInsuficienteException());
                    }
                    return Mono.error(new InventarioNoDisponibleException());
                });
    }

    private <T> T ejecutar(Mono<T> operation, String operationName) {
        try {
            T result = operation.timeout(properties.readTimeout()).block();
            if (result == null) {
                throw new InventarioNoDisponibleException();
            }
            return result;
        } catch (ApiException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            log.warn("Fallo de transporte al {} en ms-inventario", operationName);
            throw new InventarioNoDisponibleException();
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof TimeoutException) {
                log.warn("Timeout al {} en ms-inventario", operationName);
            } else {
                log.warn("Fallo inesperado al {} en ms-inventario", operationName);
            }
            throw new InventarioNoDisponibleException();
        }
    }

    private String bearer(String forwardedToken) {
        String selectedToken = properties.serviceToken().isBlank()
                ? forwardedToken
                : properties.serviceToken();
        if (selectedToken == null || selectedToken.isBlank()) {
            throw new InventarioNoDisponibleException();
        }
        return selectedToken.regionMatches(true, 0, "Bearer ", 0, 7)
                ? selectedToken
                : "Bearer " + selectedToken;
    }
}
