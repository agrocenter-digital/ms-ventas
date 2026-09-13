package com.agrocenter.ms_ventas.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class CognitoTokenUseValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN_USE = new OAuth2Error(
            "invalid_token",
            "Se requiere un access token de Cognito",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return "access".equals(jwt.getClaimAsString("token_use"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN_USE);
    }
}
