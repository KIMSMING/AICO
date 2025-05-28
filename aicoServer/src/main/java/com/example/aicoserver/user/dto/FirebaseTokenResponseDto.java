package com.example.aicoserver.user.dto;

public class FirebaseTokenResponseDto {
    private String customToken;

    public FirebaseTokenResponseDto(String customToken) {
        this.customToken = customToken;
    }

    public String getCustomToken() {
        return customToken;
    }
}
