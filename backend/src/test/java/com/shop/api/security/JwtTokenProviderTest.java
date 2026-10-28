package com.shop.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.api.common.error.ErrorCode;
import com.shop.api.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET =
            "ZGV2LW9ubHktc2hvcC1hcGktand0LXNlY3JldC1rZXktZG8tbm90LXVzZS1pbi1wcm9k";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private JwtProperties props(String secret) {
        return new JwtProperties(secret, "shop-api", 10800, 1209600);
    }

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(props(SECRET), Clock.system(ZONE));
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    @Test
    void 발급한_토큰의_클레임과_유효시간이_계약과_같다() {
        var issued = provider().createAccessToken("user01", "홍길동", "ROLE_USER");

        Claims c = Jwts.parser().verifyWith(key()).build().parseSignedClaims(issued.token()).getPayload();
        assertThat(c.getSubject()).isEqualTo("user01");
        assertThat(c.get("name", String.class)).isEqualTo("홍길동");
        assertThat(c.get("role", String.class)).isEqualTo("ROLE_USER");
        assertThat(c.getIssuer()).isEqualTo("shop-api");
        assertThat(c.getExpiration().getTime() / 1000 - c.getIssuedAt().getTime() / 1000).isEqualTo(10800);

        String header = new String(Base64.getUrlDecoder().decode(issued.token().split("\\.")[0]));
        assertThat(header).contains("\"alg\":\"HS256\"").contains("\"typ\":\"JWT\"");

        var result = provider().validate(issued.token());
        assertThat(result.isValid()).isTrue();
        assertThat(result.principal()).isEqualTo(new AuthPrincipal("user01", "홍길동", "ROLE_USER"));
    }

    @Test
    void 만료된_토큰은_TOKEN_EXPIRED() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(4)), ZONE);
        String token = new JwtTokenProvider(props(SECRET), past)
                .createAccessToken("user01", "홍길동", "ROLE_USER").token();

        var result = provider().validate(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.error()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 변조_형식오류_다른키_다른발급자는_INVALID_TOKEN() {
        String token = provider().createAccessToken("user01", "홍길동", "ROLE_USER").token();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThat(provider().validate(tampered).error()).isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThat(provider().validate("not-a-jwt").error()).isEqualTo(ErrorCode.INVALID_TOKEN);

        String otherKey = Base64.getEncoder().encodeToString(new byte[40]);
        String foreign = new JwtTokenProvider(props(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes())),
                Clock.system(ZONE)).createAccessToken("user01", "홍길동", "ROLE_USER").token();
        assertThat(provider().validate(foreign).error()).isEqualTo(ErrorCode.INVALID_TOKEN);

        String otherIssuer = Jwts.builder().issuer("evil").subject("user01").claim("role", "ROLE_USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key(), Jwts.SIG.HS256).compact();
        assertThat(provider().validate(otherIssuer).error()).isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThat(otherKey).isNotBlank();
    }

    @Test
    void 알고리즘이_HS256이_아니면_거부한다() {
        String hs512 = Jwts.builder().issuer("shop-api").subject("user01").claim("role", "ROLE_USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(new byte[64]), Jwts.SIG.HS512).compact();
        assertThat(provider().validate(hs512).error()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 키가_32바이트_미만이면_기동_실패() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new JwtTokenProvider(props(shortKey), Clock.system(ZONE)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(props("###not-base64###"), Clock.system(ZONE)))
                .isInstanceOf(IllegalStateException.class);
    }
}
