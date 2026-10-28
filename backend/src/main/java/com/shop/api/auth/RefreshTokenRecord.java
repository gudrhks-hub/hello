package com.shop.api.auth;

import java.time.LocalDateTime;

public class RefreshTokenRecord {
    private String tokenHash;
    private String userId;
    private LocalDateTime expiresAt;

    public RefreshTokenRecord() {
    }

    public RefreshTokenRecord(String tokenHash, String userId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
