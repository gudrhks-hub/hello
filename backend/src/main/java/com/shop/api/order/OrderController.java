package com.shop.api.order;

import com.shop.api.common.error.BusinessException;
import com.shop.api.common.error.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** placeholder: 조회는 빈 목록, 구매는 501. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record OrderListResponse(List<Object> items, int totalCount) {
    }

    @GetMapping
    public OrderListResponse list() {
        return new OrderListResponse(List.of(), 0);
    }

    @PostMapping
    public void create() {
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
