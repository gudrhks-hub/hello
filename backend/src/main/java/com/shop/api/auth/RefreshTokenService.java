package com.shop.api.auth;

import com.shop.api.common.error.BusinessException;
import com.shop.api.common.error.ErrorCode;
import com.shop.api.config.AppConfig;
import com.shop.api.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리프레시 토큰: 원문은 쿠키로만 전달하고 DB 에는 SHA-256 hex 만 저장한다. */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenMapper mapper;
    private final JwtProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenMapper mapper, JwtProperties props, Clock clock) {
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    /** 새 토큰을 저장하고 원문을 반환한다. 해당 사용자의 만료 행도 함께 정리한다. */
    @Transactional
    public String issue(String userId) {
        LocalDateTime now = now();
        mapper.deleteExpiredByUserId(userId, now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        mapper.insert(new RefreshTokenRecord(hash(raw), userId, now.plusSeconds(props.refreshTokenValiditySeconds())));
        return raw;
    }

    /**
     * 원문으로 유효한 행을 찾는다. 만료된 행은 삭제하고 null 을 반환한다.
     * (호출자 트랜잭션이 이 삭제를 롤백하지 않도록 AuthService 에서 noRollbackFor 를 지정한다.)
     */
    @Transactional
    public RefreshTokenRecord findValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        RefreshTokenRecord record = mapper.findByHash(hash(raw));
        if (record == null) {
            return null;
        }
        if (!record.getExpiresAt().isAfter(now())) {
            mapper.deleteByHash(record.getTokenHash());
            return null;
        }
        return record;
    }

    /** 기존 행 삭제 + 새 토큰 발급. 삭제 행이 0이면 동시 회전 경쟁으로 보고 실패 처리한다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public String rotate(RefreshTokenRecord old) {
        if (mapper.deleteByHash(old.getTokenHash()) == 0) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return issue(old.getUserId());
    }

    @Transactional
    public void revoke(String raw) {
        if (raw != null && !raw.isBlank()) {
            mapper.deleteByHash(hash(raw));
        }
    }

    public long validitySeconds() {
        return props.refreshTokenValiditySeconds();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(AppConfig.ZONE));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
