package com.shop.api.cart;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** placeholder: 로그인 필요 경로 보호 검증용. 항상 빈 목록. */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    public record CartResponse(List<Object> items, int totalCount) {
    }

    @GetMapping
    public CartResponse get() {
        return new CartResponse(List.of(), 0);
    }
}
