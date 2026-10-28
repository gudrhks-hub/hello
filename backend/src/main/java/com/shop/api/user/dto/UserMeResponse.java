package com.shop.api.user.dto;

import java.time.OffsetDateTime;

public record UserMeResponse(
        String userId,
        String userName,
        String gender,
        String birth,
        String email,
        String phone,
        String zipCode,
        String addr,
        String addrDtl,
        String role,
        String roleName,
        OffsetDateTime createAt) {
}
