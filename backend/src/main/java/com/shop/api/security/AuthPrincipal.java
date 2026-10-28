package com.shop.api.security;

/** 인증된 요청의 주체. JWT 클레임(sub, name, role)에서 복원한다. */
public record AuthPrincipal(String userId, String userName, String role) {
}
