package com.shop.api.auth.dto;

import java.time.OffsetDateTime;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        OffsetDateTime accessTokenExpiresAt,
        AuthUser user) {
}
