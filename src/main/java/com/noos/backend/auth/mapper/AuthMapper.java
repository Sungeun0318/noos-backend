package com.noos.backend.auth.mapper;

import com.noos.backend.auth.dto.SignupRequest;
import com.noos.backend.auth.dto.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    // 일반 회원가입
    void insertUser(SignupRequest request);

    // 비밀번호 검증용 로컬 계정 조회
    User findLocalUserByLoginId(@Param("loginId") String loginId);

    // 소셜 로그인 계정 저장 또는 업데이트
    void saveOrUpdateOAuthUser(User user);

    // 제공자 정보로 기존 소셜 계정 조회
    User findByProviderInfo(@Param("provider") String provider, @Param("providerId") String providerId);
}
