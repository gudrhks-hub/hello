package com.shop.api.auth;

import com.shop.api.config.CookieProperties;
import com.shop.api.config.JwtProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private final CookieProperties cookieProps;
    private final JwtProperties jwtProps;

    public RefreshTokenCookieManager(CookieProperties cookieProps, JwtProperties jwtProps) {
        this.cookieProps = cookieProps;
        this.jwtProps = jwtProps;
    }

    public ResponseCookie issueCookie(String rawToken) {
        return build(rawToken, Duration.ofSeconds(jwtProps.refreshTokenValiditySeconds()));
    }

    public ResponseCookie clearCookie() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProps.secure())
                .path(PATH)
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();
    }
}
