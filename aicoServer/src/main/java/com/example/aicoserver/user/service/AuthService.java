package com.example.aicoserver.user.service;

import com.example.aicoserver.user.dto.SocialUserDto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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

        // 2. UID 생성
        String uid = userInfo.getProviderId();

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
                if (!"uid-already-exists".equals(createEx.getErrorCode())) {
                    throw new RuntimeException("Firebase 사용자 생성 실패: " + createEx.getMessage(), createEx);
                }
            }
        }

        // 4. Realtime Database에 사용자 정보 저장
        saveUserToRealtimeDatabase(userInfo, provider, uid);

        // 5. 커스텀 토큰 생성
        try {
            return firebaseAuth.createCustomToken(uid);
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Firebase 커스텀 토큰 생성 실패: " + e.getMessage(), e);
        }
    }

    private void saveUserToRealtimeDatabase(SocialUserDto userInfo, String provider, String uid) {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", uid);
        userMap.put("email", userInfo.getEmail());
        userMap.put("name", userInfo.getName());
        userMap.put("nickname", userInfo.getNickname());
        userMap.put("birth", userInfo.getBirth());
        userMap.put("gender", userInfo.getGender());
        userMap.put("address", userInfo.getAddress());
        userMap.put("phone", userInfo.getPhone());
        userMap.put("photoUrl", userInfo.getPhotoUrl());
        userMap.put("provider", provider);

        ref.setValueAsync(userMap);
    }
}
