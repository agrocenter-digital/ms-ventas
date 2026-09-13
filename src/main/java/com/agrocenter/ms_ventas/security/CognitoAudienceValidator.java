package com.agrocenter.ms_ventas.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class CognitoAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "El token no corresponde al cliente configurado",
            null
    );

    private final String audience;

    public CognitoAudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean audienceMatches = jwt.getAudience() != null && jwt.getAudience().contains(audience);
        boolean clientMatches = audience.equals(jwt.getClaimAsString("client_id"));
        return audienceMatches || clientMatches
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
