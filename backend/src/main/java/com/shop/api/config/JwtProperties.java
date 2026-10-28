package com.shop.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @Positive long accessTokenValiditySeconds,
        @Positive long refreshTokenValiditySeconds) {

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=****]";
    }
}
