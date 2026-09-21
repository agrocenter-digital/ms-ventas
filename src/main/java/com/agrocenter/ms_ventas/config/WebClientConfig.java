package com.agrocenter.ms_ventas.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient inventoryWebClient(InventoryProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.connectTimeout().toMillis())
                )
                .responseTimeout(properties.readTimeout());

        String resolvedUrl = sanitizeBaseUrl(properties.baseUrl());

        return WebClient.builder()
                .baseUrl(resolvedUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String sanitizeBaseUrl(String url) {
        if (url == null || url.isBlank() || (url.startsWith("${") && url.endsWith("}"))) {
            return "http://internal-agrocenter-bff-alb:8080";
        }
        String clean = url.trim().replaceAll("/+$", "");
        clean = clean.replaceAll("/api/inventario/?$", "");
        return clean.replaceAll("/+$", "");
    }
}
