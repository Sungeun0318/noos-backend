package com.noos.backend.mobile.auth.mapper;

import com.noos.backend.mobile.auth.dto.MobileUserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserDirectoryMapper {

    void insertLocalUser(MobileUserRow row);

    MobileUserRow findLocalUserByLoginId(@Param("loginId") String loginId);

    MobileUserRow findById(@Param("userId") Long userId);
}
