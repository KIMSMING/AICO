package com.example.aicoserver.user.service;

import com.example.aicoserver.user.dto.SocialUserDto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final KakaoAuthService kakaoAuthService;
    private final NaverAuthService naverAuthService;

    public String handleSocialLogin(String provider, String accessToken) {
        // 1. 소셜 플랫폼에서 사용자 정보 조회
        SocialUserDto userInfo;
        if ("kakao".equalsIgnoreCase(provider)) {
            userInfo = kakaoAuthService.getUserInfo(accessToken);
        } else if ("naver".equalsIgnoreCase(provider)) {
            userInfo = naverAuthService.getUserInfo(accessToken);
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        // 2. UID 생성 (형식: "kakao:12345", "naver:abcde")
        String uid = provider + ":" + userInfo.getProviderId();

        // 3. Firebase 사용자 생성/조회
        try {
            firebaseAuth.getUser(uid);
        } catch (FirebaseAuthException e) {
            // 사용자가 없을 때만 새로 생성
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setUid(uid)
                    .setEmail(userInfo.getEmail())
                    .setDisplayName(userInfo.getNickname());
            try {
                firebaseAuth.createUser(request);
            } catch (FirebaseAuthException createEx) {
                // 이미 존재하는 경우 등은 무시, 그 외는 예외 처리
                if (!"uid-already-exists".equals(createEx.getErrorCode())) {
                    throw new RuntimeException("Firebase 사용자 생성 실패: " + createEx.getMessage(), createEx);
                }
            }
        }

        // 4. 커스텀 토큰 생성 (추가 claims 없이 기본 사용)
        try {
            return firebaseAuth.createCustomToken(uid);
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Firebase 커스텀 토큰 생성 실패: " + e.getMessage(), e);
        }
    }
}
