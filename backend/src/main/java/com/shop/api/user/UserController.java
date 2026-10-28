package com.shop.api.user;

import com.shop.api.security.AuthPrincipal;
import com.shop.api.user.dto.UserMeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getMe(principal.userId());
    }
}
