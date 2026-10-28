package com.shop.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(max = 50, message = "아이디는 50자 이하로 입력해 주세요.")
        String userId,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 100, message = "비밀번호는 100자 이하로 입력해 주세요.")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[userId=" + userId + ", password=****]";
    }
}
