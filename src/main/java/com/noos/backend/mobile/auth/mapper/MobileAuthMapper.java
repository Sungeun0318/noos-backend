package com.noos.backend.mobile.auth.mapper;

import com.noos.backend.mobile.auth.dto.MobileAuthTokenRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MobileAuthMapper {

    void insert(MobileAuthTokenRow row);

    MobileAuthTokenRow findActiveByRefreshToken(@Param("refreshToken") String refreshToken);

    MobileAuthTokenRow findByAccessJti(@Param("accessJti") String accessJti);

    void revokeById(@Param("id") String id);

    void touchLastUsed(@Param("id") String id);
}
