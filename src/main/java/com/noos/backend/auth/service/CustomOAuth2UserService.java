package com.noos.backend.auth.service;


import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.mapper.AuthMapper;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthMapper authMapper;

    public CustomOAuth2UserService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // "google" 또는 "github"
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String providerId = "";
        String email = "";
        String name = "";

        //플랫폼별로 데이터를 다르게 뽑아냄
        if ("google".equals(registrationId)) {
            providerId = String.valueOf(attributes.get("sub"));
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
        } else if ("github".equals(registrationId)) {
            providerId = String.valueOf(attributes.get("id"));
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");

            // 깃허브는 이름을 안 적은 유저시 -> Login id로 그냥 넣어버림
            if (name == null) {
                name = (String) attributes.get("login");
            }
        }

        System.out.println(registrationId + " 로그인 정보 저장 시작: " + email);

        User user = new User();
        user.setLoginId(email);
        user.setDisplayName(name);
        user.setProvider(registrationId);
        user.setProviderId(providerId);

        authMapper.saveOrUpdateOAuthUser(user);

        return oAuth2User;
    }
}