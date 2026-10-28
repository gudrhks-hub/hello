package com.shop.api.auth;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefreshTokenMapper {

    int insert(RefreshTokenRecord record);

    RefreshTokenRecord findByHash(@Param("tokenHash") String tokenHash);

    int deleteByHash(@Param("tokenHash") String tokenHash);

    int deleteExpiredByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
