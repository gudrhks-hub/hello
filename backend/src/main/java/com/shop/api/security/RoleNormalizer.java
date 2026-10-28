package com.shop.api.security;

public final class RoleNormalizer {

    public static final String DEFAULT_ROLE = "ROLE_USER";
    private static final String PREFIX = "ROLE_";

    private RoleNormalizer() {
    }

    /** null/공백 -> ROLE_USER, ROLE_ 접두사가 없으면 붙인다 (plan D4). */
    public static String normalize(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return DEFAULT_ROLE;
        }
        String trimmed = roleId.trim();
        return trimmed.startsWith(PREFIX) ? trimmed : PREFIX + trimmed;
    }
}
