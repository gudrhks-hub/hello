package com.shop.api.user;

import com.shop.api.common.error.BusinessException;
import com.shop.api.common.error.ErrorCode;
import com.shop.api.config.AppConfig;
import com.shop.api.security.RoleNormalizer;
import com.shop.api.user.domain.User;
import com.shop.api.user.dto.UserMeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(String userId) {
        User u = userMapper.findByUserId(userId);
        if (u == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return new UserMeResponse(
                u.getUserId(), u.getUserName(), u.getGender(), u.getBirth(), u.getEmail(), u.getPhone(),
                u.getZipCode(), u.getAddr(), u.getAddrDtl(),
                RoleNormalizer.normalize(u.getRoleId()), u.getRoleName(),
                u.getCreateAt() == null ? null : u.getCreateAt().atZone(AppConfig.ZONE).toOffsetDateTime());
    }
}
