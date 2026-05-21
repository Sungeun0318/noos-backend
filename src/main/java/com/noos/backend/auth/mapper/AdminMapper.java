package com.noos.backend.auth.mapper;

import com.noos.backend.auth.dto.UpdateUserRequest;
import com.noos.backend.auth.dto.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMapper {
    List<User> findAllUsers();

    void deleteUser(@Param("userId") Long userId);

    void updateUser(@Param("userId") Long userId, @Param("request") UpdateUserRequest request);

    List<User> searchUsers(
            @Param("type") String type,
            @Param("keyword") String keyword,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
