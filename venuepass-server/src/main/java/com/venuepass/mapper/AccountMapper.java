package com.venuepass.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    @Select("SELECT password_hash FROM user_account WHERE username=#{username}")
    String passwordHash(@Param("username") String username);

    @Insert("INSERT INTO user_account(username,password_hash) VALUES(#{username},#{hash})")
    void insert(@Param("username") String username, @Param("hash") String hash);
}
