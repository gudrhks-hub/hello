package com.shop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.api.auth.dto.LoginRequest;
import com.shop.api.common.error.BusinessException;
import com.shop.api.common.error.ErrorCode;
import com.shop.api.config.JwtProperties;
import com.shop.api.security.JwtTokenProvider;
import com.shop.api.user.UserMapper;
import com.shop.api.user.domain.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String SECRET =
            "ZGV2LW9ubHktc2hvcC1hcGktand0LXNlY3JldC1rZXktZG8tbm90LXVzZS1pbi1wcm9k";

    @Mock UserMapper userMapper;
    @Mock RefreshTokenService refreshTokenService;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, "shop-api", 10800, 1209600);
        JwtTokenProvider provider = new JwtTokenProvider(props, Clock.system(ZoneId.of("Asia/Seoul")));
        service = new AuthService(userMapper, encoder, provider, refreshTokenService);
        when(refreshTokenService.issue(anyString())).thenReturn("raw-refresh");
    }

    private User user(String roleId) {
        User u = new User();
        u.setUserId("user01");
        u.setUserName("홍길동");
        u.setPasswd(encoder.encode("Passw0rd!"));
        u.setRoleId(roleId);
        return u;
    }

    @Test
    void 로그인_성공() {
        when(userMapper.findByUserId("user01")).thenReturn(user("ROLE_USER"));

        var result = service.login(new LoginRequest("user01", "Passw0rd!"));

        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        var body = result.tokenResponse();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(10800);
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.user().userId()).isEqualTo("user01");
        assertThat(body.user().userName()).isEqualTo("홍길동");
        assertThat(body.user().role()).isEqualTo("ROLE_USER");
        assertThat(body.accessTokenExpiresAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
    }

    @Test
    void 아이디가_없으면_INVALID_CREDENTIALS() {
        when(userMapper.findByUserId("nobody")).thenReturn(null);

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody", "Passw0rd!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(refreshTokenService, never()).issue(anyString());
    }

    @Test
    void 비밀번호가_틀리면_INVALID_CREDENTIALS() {
        when(userMapper.findByUserId("user01")).thenReturn(user("ROLE_USER"));

        assertThatThrownBy(() -> service.login(new LoginRequest("user01", "wrong")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void role_id가_null이면_ROLE_USER() {
        when(userMapper.findByUserId("user01")).thenReturn(user(null));
        assertThat(service.login(new LoginRequest("user01", "Passw0rd!")).tokenResponse().user().role())
                .isEqualTo("ROLE_USER");
    }

    @Test
    void role_id에_접두사가_없으면_붙인다() {
        when(userMapper.findByUserId("user01")).thenReturn(user("ADMIN"));
        assertThat(service.login(new LoginRequest("user01", "Passw0rd!")).tokenResponse().user().role())
                .isEqualTo("ROLE_ADMIN");
    }

    @Test
    void 리프레시_토큰이_유효하지_않으면_REFRESH_TOKEN_INVALID() {
        when(refreshTokenService.findValid("bad")).thenReturn(null);

        assertThatThrownBy(() -> service.refresh("bad"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    void 리프레시_시_사용자가_삭제되었으면_REFRESH_TOKEN_INVALID() {
        var rec = new RefreshTokenRecord("h", "ghost", LocalDateTime.now().plusDays(1));
        when(refreshTokenService.findValid("raw")).thenReturn(rec);
        when(userMapper.findByUserId("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(BusinessException.class);
        verify(refreshTokenService, never()).rotate(any());
    }

    @Test
    void 리프레시_성공_시_최신_사용자정보로_발급하고_회전한다() {
        var rec = new RefreshTokenRecord("h", "user01", LocalDateTime.now().plusDays(1));
        when(refreshTokenService.findValid("raw")).thenReturn(rec);
        when(refreshTokenService.rotate(rec)).thenReturn("new-raw");
        when(userMapper.findByUserId("user01")).thenReturn(user("ROLE_ADMIN"));

        var result = service.refresh("raw");

        assertThat(result.refreshToken()).isEqualTo("new-raw");
        assertThat(result.tokenResponse().user().role()).isEqualTo("ROLE_ADMIN");
    }
}
