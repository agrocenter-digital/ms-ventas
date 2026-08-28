package com.agrocenter.ms_ventas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agrocenter.inventory")
public record InventoryProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String serviceToken
) {
    public InventoryProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("La URL de ms-inventario es obligatoria");
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        serviceToken = serviceToken == null ? "" : serviceToken.trim();
    }
}
