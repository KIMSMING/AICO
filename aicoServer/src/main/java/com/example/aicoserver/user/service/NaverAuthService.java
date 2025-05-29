package com.example.aicoserver.user.service;

import com.example.aicoserver.user.dto.SocialUserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class NaverAuthService {

    private static final String NAVER_USERINFO_URL = "https://openapi.naver.com/v1/nid/me";

    public SocialUserDto getUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                NAVER_USERINFO_URL,
                HttpMethod.GET,
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("네이버 사용자 정보 조회 실패");
        }

        Map<String, Object> body = response.getBody();
        Map<String, Object> responseMap = (Map<String, Object>) body.get("response");

        String id = responseMap != null ? (String) responseMap.get("id") : null;
        String email = responseMap != null ? (String) responseMap.get("email") : null;
        String name = responseMap != null ? (String) responseMap.get("name") : null;
        String nickname = responseMap != null ? (String) responseMap.get("nickname") : null;
        String birth = responseMap != null ? (String) responseMap.get("birth") : null;
        String gender = responseMap != null ? (String) responseMap.get("gender") : null;
        String address = responseMap != null ? (String) responseMap.get("address") : null;
        String phone = responseMap != null ? (String) responseMap.get("mobile") : null;
        String photoUrl = responseMap != null ? (String) responseMap.get("profile_image") : null;

        // 필수값만 체크
        if (id == null || email == null || nickname == null) {
            throw new RuntimeException("네이버 필수 사용자 정보 누락(id, email, nickname)");
        }

        SocialUserDto userDto = new SocialUserDto();
        userDto.setProviderId(id);
        userDto.setEmail(email);
        userDto.setName(name);
        userDto.setNickname(nickname);
        userDto.setBirth(birth);
        userDto.setGender(gender);
        userDto.setAddress(address);
        userDto.setPhone(phone);
        userDto.setPhotoUrl(photoUrl);

        log.info("네이버 사용자 정보: {}", userDto);
        return userDto;
    }
}
