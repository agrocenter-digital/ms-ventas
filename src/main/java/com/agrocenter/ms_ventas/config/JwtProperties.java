package com.agrocenter.ms_ventas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agrocenter.security.jwt")
public record JwtProperties(
        String issuerUri,
        String jwkSetUri,
        String audience
) {
    public JwtProperties {
        requireText(issuerUri, "COGNITO_ISSUER_URI");
        requireText(jwkSetUri, "COGNITO_JWK_SET_URI");
        requireText(audience, "COGNITO_AUDIENCE");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " es obligatorio");
        }
    }
}
