// KakaoAuthService.java
package com.example.aicoserver.user.service;

import com.example.aicoserver.user.dto.SocialUserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class KakaoAuthService {
    private static final String KAKAO_USERINFO_URL = "https://kapi.kakao.com/v2/user/me";

    public SocialUserDto getUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                KAKAO_USERINFO_URL,
                HttpMethod.GET,
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("카카오 사용자 정보 조회 실패");
        }

        Map<String, Object> body = response.getBody();
        String id = String.valueOf(body.get("id"));

        Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        String name = kakaoAccount != null ? (String) kakaoAccount.get("name") : null;
        String birth = kakaoAccount != null ? (String) kakaoAccount.get("birth") : null;
        String gender = kakaoAccount != null ? (String) kakaoAccount.get("gender") : null;

        Map<String, Object> properties = (Map<String, Object>) body.get("properties");
        String nickname = properties != null ? (String) properties.get("nickname") : null;
        String profileImage = properties != null ? (String) properties.get("profile_image") : null;

        if (id == null || email == null || nickname == null) {
            throw new RuntimeException("카카오 필수 사용자 정보 누락(id, email, nickname)");
        }

        SocialUserDto userDto = new SocialUserDto();
        userDto.setProviderId(id);
        userDto.setEmail(email);
        userDto.setName(name);
        userDto.setNickname(nickname);
        userDto.setBirth(birth);
        userDto.setGender(gender);
        userDto.setPhotoUrl(profileImage);

        log.info("카카오 사용자 정보: {}", userDto);
        return userDto;
    }
}
