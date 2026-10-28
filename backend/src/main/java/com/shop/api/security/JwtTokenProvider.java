package com.shop.api.security;

import com.shop.api.common.error.ErrorCode;
import com.shop.api.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenValiditySeconds;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties props, Clock clock) {
        this.key = buildKey(props.secret());
        this.issuer = props.issuer();
        this.accessTokenValiditySeconds = props.accessTokenValiditySeconds();
        this.clock = clock;
    }

    private static SecretKey buildKey(String secret) {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("app.jwt.secret 은 Base64 문자열이어야 합니다.");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret 은 디코딩 후 32바이트 이상이어야 합니다.");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public IssuedAccessToken createAccessToken(String userId, String userName, String role) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(accessTokenValiditySeconds);
        String token = Jwts.builder()
                .header().type("JWT").and()
                .issuer(issuer)
                .subject(userId)
                .claim("name", userName)
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, issuedAt, expiresAt);
    }

    public TokenValidation validate(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
                return TokenValidation.failure(ErrorCode.INVALID_TOKEN);
            }
            Claims c = jws.getPayload();
            String sub = c.getSubject();
            String name = c.get("name", String.class);
            String role = c.get("role", String.class);
            if (sub == null || sub.isBlank() || role == null || role.isBlank()) {
                return TokenValidation.failure(ErrorCode.INVALID_TOKEN);
            }
            return TokenValidation.success(new AuthPrincipal(sub, name, role));
        } catch (ExpiredJwtException e) {
            return TokenValidation.failure(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            return TokenValidation.failure(ErrorCode.INVALID_TOKEN);
        }
    }

    public record IssuedAccessToken(String token, Instant issuedAt, Instant expiresAt) {
    }

    public record TokenValidation(AuthPrincipal principal, ErrorCode error) {

        static TokenValidation success(AuthPrincipal p) {
            return new TokenValidation(p, null);
        }

        static TokenValidation failure(ErrorCode e) {
            return new TokenValidation(null, e);
        }

        public boolean isValid() {
            return principal != null;
        }
    }
}
