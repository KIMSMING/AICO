package com.example.aicoserver.user.controller;

import com.example.aicoserver.user.dto.FirebaseTokenResponseDto;
import com.example.aicoserver.user.dto.SocialLoginRequestDto;
import com.example.aicoserver.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao")
    public ResponseEntity<FirebaseTokenResponseDto> kakaoLogin(@RequestBody SocialLoginRequestDto request) {
        try {
            String customToken = authService.handleSocialLogin("kakao", request.getAccessToken());
            return ResponseEntity.ok(new FirebaseTokenResponseDto(customToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new FirebaseTokenResponseDto("카카오 로그인 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/naver")
    public ResponseEntity<FirebaseTokenResponseDto> naverLogin(@RequestBody SocialLoginRequestDto request) {
        try {
            String customToken = authService.handleSocialLogin("naver", request.getAccessToken());
            return ResponseEntity.ok(new FirebaseTokenResponseDto(customToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new FirebaseTokenResponseDto("네이버 로그인 실패: " + e.getMessage()));
        }
    }
}
