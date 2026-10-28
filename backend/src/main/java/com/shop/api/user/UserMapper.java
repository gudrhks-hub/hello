package com.shop.api.user;

import com.shop.api.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByUserId(@Param("userId") String userId);
}
