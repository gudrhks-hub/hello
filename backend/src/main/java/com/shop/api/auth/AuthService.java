package com.shop.api.auth;

import com.shop.api.auth.dto.AuthUser;
import com.shop.api.auth.dto.LoginRequest;
import com.shop.api.auth.dto.TokenResponse;
import com.shop.api.common.error.BusinessException;
import com.shop.api.common.error.ErrorCode;
import com.shop.api.config.AppConfig;
import com.shop.api.security.JwtTokenProvider;
import com.shop.api.security.RoleNormalizer;
import com.shop.api.user.UserMapper;
import com.shop.api.user.domain.User;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final String dummyHash;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        // 존재하지 않는 아이디에도 matches 를 수행해 응답 시간 차이로 계정 존재 여부가 드러나는 것을 줄인다.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** 액세스 토큰 응답 + 쿠키에 담을 리프레시 토큰 원문. */
    public record AuthResult(TokenResponse tokenResponse, String refreshToken) {
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userMapper.findByUserId(request.userId());
        String hash = user == null ? dummyHash : user.getPasswd();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        String refresh = refreshTokenService.issue(user.getUserId());
        return new AuthResult(buildTokenResponse(user), refresh);
    }

    /** 만료 행 삭제가 예외로 롤백되지 않도록 BusinessException 은 롤백 제외. */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenRecord record = refreshTokenService.findValid(rawRefreshToken);
        if (record == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        User user = userMapper.findByUserId(record.getUserId());
        if (user == null) {
            refreshTokenService.revoke(rawRefreshToken);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String newRefresh = refreshTokenService.rotate(record);
        return new AuthResult(buildTokenResponse(user), newRefresh);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private TokenResponse buildTokenResponse(User user) {
        String role = RoleNormalizer.normalize(user.getRoleId());
        JwtTokenProvider.IssuedAccessToken issued =
                jwtTokenProvider.createAccessToken(user.getUserId(), user.getUserName(), role);
        long expiresIn = issued.expiresAt().getEpochSecond() - issued.issuedAt().getEpochSecond();
        OffsetDateTime expiresAt = issued.expiresAt().atZone(AppConfig.ZONE).toOffsetDateTime();
        return new TokenResponse(issued.token(), TOKEN_TYPE, expiresIn, expiresAt,
                new AuthUser(user.getUserId(), user.getUserName(), role));
    }
}
